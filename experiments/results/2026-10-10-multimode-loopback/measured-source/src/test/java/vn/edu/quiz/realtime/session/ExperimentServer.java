package vn.edu.quiz.realtime.session;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.*;
import vn.edu.quiz.QuizApplication;
import vn.edu.quiz.realtime.message.command.GameCommand;

/** Opt-in experiment entry point on TEST classpath only. Never packaged in the demo JAR. */
public final class ExperimentServer {
    public static void main(String[] args) {
        if (!System.getProperty("experiment.mode", "").matches("baseline|proposed"))
            throw new IllegalArgumentException("Set -Dexperiment.mode=baseline or proposed");
        SpringApplication.run(new Class<?>[]{QuizApplication.class, Instrumentation.class}, args);
    }

    @Configuration(proxyBeanMethods=false) @Profile("experiment")
    public static class Instrumentation {
        @Bean static BeanPostProcessor experimentRuntimeObserver() {
            final String mode=System.getProperty("experiment.mode");
            final Path output=Path.of(System.getProperty("experiment.metrics"));
            try {
                Files.createDirectories(output.toAbsolutePath().getParent());
                Files.writeString(output,"mode,operation,user_id,game_id,request_id,duration_ms,outcome\n",StandardCharsets.UTF_8);
            } catch (Exception e) { throw new IllegalStateException(e); }
            return new BeanPostProcessor() {
                public Object postProcessAfterInitialization(Object bean,String name) {
                    if (!(bean instanceof GameRuntime runtime)) return bean;
                    ProxyFactory proxy=new ProxyFactory(runtime); proxy.setProxyTargetClass(true);
                    proxy.addAdvice((MethodInterceptor) invocation -> {
                        String method=invocation.getMethod().getName();
                        if (!method.equals("command") && !method.equals("reconnect")) return invocation.proceed();
                        Object[] args=invocation.getArguments();
                        GameCommand command=method.equals("command")?(GameCommand)args[1]:null;
                        long user=method.equals("command")?(long)args[0]:(long)args[1];
                        long game=command!=null?command.target().id():(long)args[0];
                        long start=System.nanoTime();
                        var result=(CompletableFuture<?>)invocation.proceed();
                        return result.handle((value,failure) -> {
                            // Wrapped ACK delivery waits for erasure; loss trials retry only after that delivery.
                            if (failure==null && command!=null && mode.equals("baseline")) eraseReceipts(runtime,game);
                            double elapsed=(System.nanoTime()-start)/1_000_000.0;
                            String row=mode+","+(command==null?"RECONNECT":command.type())+","+user+","+game+","+
                                    (command==null?"":command.requestId())+","+String.format(java.util.Locale.ROOT,"%.6f",elapsed)+","+
                                    (failure==null?"SUCCESS":"REJECTED_OR_ERROR")+"\n";
                            synchronized(output) {
                                try { Files.writeString(output,row,StandardCharsets.UTF_8,StandardOpenOption.APPEND); }
                                catch(Exception e) { throw new CompletionException(e); }
                            }
                            if(failure!=null) throw new CompletionException(failure);
                            return value;
                        });
                    });
                    return proxy.getProxy();
                }
            };
        }
    }

    /** Experimental ablation: remove committed receipts, not guards, entities, resources or session queue. */
    static void eraseReceipts(GameRuntime runtime,long game) {
        try {
            var nodes=(Map<?,?>)field(GameRuntime.class,"nodes").get(runtime);
            Object node=nodes.get(game);
            Object cache=field(node.getClass(),"replay").get(node);
            synchronized(cache) {
                var receipts=(Map<?,?>)field(cache.getClass(),"receipts").get(cache);
                var counts=(Map<?,?>)field(cache.getClass(),"counts").get(cache);
                var budget=(Semaphore)field(cache.getClass(),"budget").get(cache);
                budget.release(receipts.size()); receipts.clear(); counts.clear();
            }
        } catch(ReflectiveOperationException e) {
            throw new IllegalStateException("Experiment no longer matches runtime/cache structure; do not claim baseline",e);
        }
    }
    private static Field field(Class<?> owner,String name) throws ReflectiveOperationException {
        Field f=owner.getDeclaredField(name); f.setAccessible(true); return f;
    }
}
