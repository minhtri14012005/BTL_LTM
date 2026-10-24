import { Button } from "@/components/ui/button";
import { Link } from "react-router-dom";
import { StudentIdentityEditor } from "./identity-editor";
import { StudentFrame } from "./student-shared";
export function StudentIdentityPage() {
  return (
    <StudentFrame title="Thông tin cá nhân">
      <Button asChild variant="outline">
        <Link to="/app/sinh_vien/profile">Về hồ sơ & thành tích</Link>
      </Button>
      <div className="max-w-3xl">
        <StudentIdentityEditor reload={() => {}} />
      </div>
    </StudentFrame>
  );
}
