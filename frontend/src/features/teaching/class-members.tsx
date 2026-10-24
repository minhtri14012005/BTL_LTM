import { EmptyState } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Confirm } from "@/components/ui/confirm";
import { Table } from "@/components/ui/table";
import { post } from "@/lib/api";
import { requestId } from "@/lib/request-id";
import { statusNames } from "@/lib/types";
import { useState } from "react";
import { type Member } from "./teaching-api";
import { FormError, Notice, useTeachingAction } from "./teaching-shared";
export function Members({
  classId,
  members,
  refresh,
}: {
  classId: number;
  members: Member[];
  refresh: () => void;
}) {
  const action = useTeachingAction();
  const [remove, setRemove] = useState<Member | null>(null);
  async function decision(member: Member, command: string) {
    await action.run(async () => {
      await post(
        `/api/teaching/classes/${classId}/members/${member.studentId}/${command}`,
        { requestId: requestId(), revision: member.revision },
      );
      setRemove(null);
      refresh();
    }, "Đã cập nhật thành viên.");
  }
  return (
    <div className="space-y-4">
      <Notice>
        Duyệt/từ chối một lần cho mỗi lớp. Loại khỏi lớp sẽ chặn đăng ký các lớp
        cùng môn và cùng năm học; lịch sử được giữ.
      </Notice>
      <FormError message={action.error} />
      {!members.length ? (
        <EmptyState
          title="Chưa có yêu cầu tham gia"
          description="Chia sẻ mã lớp cố định cho sinh viên."
        />
      ) : (
        <Card>
          <Table>
            <thead>
              <tr>
                <th>Mã SV</th>
                <th>Họ tên</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.studentId}>
                  <td>{m.maSinhVien}</td>
                  <td>{m.hoTen}</td>
                  <td>{statusNames[m.status]}</td>
                  <td>
                    <div className="flex flex-wrap gap-2">
                      {m.status === "PENDING" && (
                        <>
                          <Button
                            size="sm"
                            disabled={action.pending}
                            onClick={() => {
                              void decision(m, "approve");
                            }}
                          >
                            Duyệt {m.maSinhVien}
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={action.pending}
                            onClick={() => {
                              void decision(m, "reject");
                            }}
                          >
                            Từ chối {m.maSinhVien}
                          </Button>
                        </>
                      )}
                      {m.status === "APPROVED" && (
                        <Button
                          variant="destructive"
                          size="sm"
                          disabled={action.pending}
                          onClick={() => setRemove(m)}
                        >
                          Loại {m.maSinhVien}
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}
      <Confirm
        open={!!remove}
        onOpenChange={(open) => {
          if (!action.pending && !open) setRemove(null);
        }}
        title="Loại sinh viên khỏi môn trong năm học?"
        description={`${remove?.hoTen} · ${remove?.maSinhVien}. Sinh viên không thể đăng ký lớp khác cùng môn và năm học. Điểm và lịch sử được giữ.`}
        error={action.error}
        pending={action.pending}
        onConfirm={() => {
          if (remove) void decision(remove, "remove");
        }}
      />
    </div>
  );
}
