import { EmptyState } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Confirm } from "@/components/ui/confirm";
import { Table } from "@/components/ui/table";
import { post } from "@/lib/api";
import { requestId } from "@/lib/request-id";
import { statusNames } from "@/lib/types";
import { useEffect, useRef, useState } from "react";
import { type Member } from "./teaching-api";
import { FormError, Notice, useTeachingAction } from "./teaching-shared";
export interface BulkApprovalResult {
  approved: number;
  invalid: number;
  skipped: { studentId: number; reason: string }[];
}
export function Members({
  classId,
  members,
  refresh,
  bulkResult: savedBulk,
  onBulkResult,
  onBusyChange,
}: {
  classId: number;
  members: Member[];
  refresh: () => void;
  bulkResult?: BulkApprovalResult;
  onBulkResult?: (result: BulkApprovalResult) => void;
  onBusyChange?: (pending: boolean) => void;
}) {
  const action = useTeachingAction();
  const [remove, setRemove] = useState<Member | null>(null);
  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulkResult, setBulkResult] = useState<BulkApprovalResult>();
  const result = savedBulk ?? bulkResult;
  useEffect(() => {
    onBusyChange?.(action.pending);
    return () => onBusyChange?.(false);
  }, [action.pending, onBusyChange]);
  const bulkRequest = useRef("");
  const pendingCount = members.filter((m) => m.status === "PENDING").length;
  const reasons: Record<string, string> = {
    ACCOUNT_UNAVAILABLE: "Tài khoản đã bị hạn chế hoặc xóa",
    SUBJECT_YEAR_REMOVED: "Đã bị loại khỏi môn trong năm học",
    ALREADY_REVIEWED: "Yêu cầu đã được xử lý",
  };
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
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="font-medium">Yêu cầu đang chờ: {pendingCount}</p>
        <Button
          disabled={action.pending || !pendingCount}
          onClick={() => {
            bulkRequest.current = requestId();
            setBulkOpen(true);
          }}
        >
          Duyệt tất cả ({pendingCount})
        </Button>
      </div>
      {result && (
        <div
          role="status"
          className="rounded-2xl border bg-accent/40 p-4 text-sm"
        >
          <p>
            Đã duyệt {result.approved} yêu cầu. Không hợp lệ: {result.invalid}.
          </p>
          {!!result.skipped.length && (
            <ul className="mt-2 list-disc space-y-1 pl-5">
              {result.skipped.map((s) => (
                <li key={s.studentId}>
                  {members.find((m) => m.studentId === s.studentId)?.hoTen ??
                    `Sinh viên ${s.studentId}`}
                  : {reasons[s.reason] ?? s.reason}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
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
                      {m.status === "REMOVED" && (
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={action.pending}
                          onClick={() => setRemove(m)}
                        >
                          Khôi phục {m.maSinhVien}
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
        title={
          remove?.status === "REMOVED"
            ? "Khôi phục sinh viên trong môn và năm học?"
            : "Loại sinh viên khỏi môn trong năm học?"
        }
        description={
          remove?.status === "REMOVED"
            ? `${remove.hoTen} · ${remove.maSinhVien}. Khôi phục trạng thái thành viên trước khi loại tại các lớp cùng môn/năm học. Cần quyền quản lý lớp đã thực hiện thao tác loại. Không thay đổi hạn chế tài khoản của ADMIN; điểm và lịch sử giữ nguyên.`
            : `${remove?.hoTen} · ${remove?.maSinhVien}. Sinh viên không thể đăng ký lớp khác cùng môn và năm học. Điểm và lịch sử được giữ.`
        }
        error={action.error}
        pending={action.pending}
        onConfirm={() => {
          if (remove)
            void decision(
              remove,
              remove.status === "REMOVED" ? "restore" : "remove",
            );
        }}
      />
      <Confirm
        open={bulkOpen}
        onOpenChange={(open) => {
          if (!action.pending) setBulkOpen(open);
        }}
        title="Duyệt tất cả yêu cầu đang chờ?"
        description={`Server sẽ kiểm tra ${pendingCount} yêu cầu của lớp này. Sinh viên bị hạn chế hoặc đã bị loại sẽ không được duyệt.`}
        pending={action.pending}
        error={action.error}
        onConfirm={() => {
          void action.run(async () => {
            const result = await post<{
              approved: number;
              invalid: number;
              skipped: { studentId: number; reason: string }[];
            }>(`/api/teaching/classes/${classId}/members/approve-all`, {
              requestId: bulkRequest.current,
            });
            setBulkResult(result);
            onBulkResult?.(result);
            setBulkOpen(false);
            refresh();
          });
        }}
      />
    </div>
  );
}
