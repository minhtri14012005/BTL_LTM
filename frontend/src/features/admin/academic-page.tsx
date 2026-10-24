import { ErrorState, Loading } from "@/components/feedback";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useResource } from "@/hooks/use-resource";
import { useSearchParams } from "react-router-dom";
import { MetadataPanel } from "./academic-metadata";
import { OfferingsPanel } from "./academic-offerings";
import { loadCatalog } from "./admin-api";
import { AdminFrame } from "./admin-shared";

const sections = {
  years: "Năm học",
  semesters: "Học kỳ",
  subjects: "Môn học",
  offerings: "Môn theo học kỳ",
};
type Section = keyof typeof sections;
export function AcademicPage() {
  const resource = useResource(loadCatalog, []);
  const [params, setParams] = useSearchParams();
  const tab = (params.get("step") || "years") as Section;
  const current = Object.hasOwn(sections, tab) ? tab : "years";
  return (
    <AdminFrame
      title="Tổ chức đào tạo"
      description="Thiết lập theo thứ tự: năm học → học kỳ → môn học → mở môn theo học kỳ. Sau đó tạo lớp và phân công giảng viên."
      reload={resource.reload}
    >
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <Tabs
            value={current}
            onValueChange={(value) => setParams({ step: value })}
          >
            <div className="overflow-x-auto pb-1">
              <TabsList aria-label="Các bước đào tạo">
                {Object.entries(sections).map(([key, label], i) => (
                  <TabsTrigger key={key} value={key}>
                    {i + 1}. {label}
                  </TabsTrigger>
                ))}
              </TabsList>
            </div>
            {(["years", "semesters", "subjects"] as const).map((key) => (
              <TabsContent key={key} value={key}>
                <MetadataPanel
                  section={key}
                  catalog={resource.data!}
                  saved={resource.reload}
                />
              </TabsContent>
            ))}
            <TabsContent value="offerings">
              <OfferingsPanel catalog={resource.data} saved={resource.reload} />
            </TabsContent>
          </Tabs>
        )
      )}
    </AdminFrame>
  );
}
