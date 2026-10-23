import {cleanup,render,screen,fireEvent,waitFor} from "@testing-library/react";
import {afterEach,it,expect,vi} from "vitest";
import {LessonPlan} from "./lesson-plan";
vi.mock("@/lib/api",async original=>({...await original<typeof import("@/lib/api")>(),api:vi.fn(async(url:string)=>({items:[{id:url.includes("QUIZ")?10:20,title:url.includes("scope=MINE")?"Bộ riêng":"Bộ chia sẻ",visibility:url.includes("scope=MINE")?"PRIVATE":"PUBLIC"}],totalElements:1}))}));
afterEach(cleanup);
it("selects an owned or Public bank, saves its slot and clears it when the game mode changes",async()=>{
 const save=vi.fn();render(<LessonPlan onSave={save} pending={false} error=""/>);
 await waitFor(()=>expect(screen.getAllByRole("option",{name:"Bộ riêng · PRIVATE"})).toHaveLength(2));
 fireEvent.change(screen.getByLabelText("Nguồn bộ câu hỏi trò 2"),{target:{value:"SHARED"}});
 await screen.findByRole("option",{name:"Bộ chia sẻ · PUBLIC"});
 fireEvent.change(screen.getByLabelText("Bộ câu hỏi / đề bài trò 2"),{target:{value:"10"}});
 fireEvent.change(screen.getByLabelText("Tên buổi học"),{target:{value:"Buổi kiểm tra"}});
 for(const i of [1,2,3])fireEvent.change(screen.getByLabelText(`Ngân sách trò ${i}`),{target:{value:"20"}});
 fireEvent.click(screen.getByRole("button",{name:"Tạo buổi học"}));
 await waitFor(()=>expect(save).toHaveBeenCalled());expect(save.mock.calls[0][1][2]).toBe(10);expect(save.mock.calls[0][0].slots[1].title).toBe("Quiz");
 fireEvent.change(screen.getByLabelText("Mode trò 2"),{target:{value:"ASSIGNMENT"}});
 await waitFor(()=>expect((screen.getByLabelText("Bộ câu hỏi / đề bài trò 2") as HTMLSelectElement).value).toBe("0"));
});
