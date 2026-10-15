# THIẾT KẾ NÂNG CẤP MULTIGAME
## Vinh danh sau trận · Hồ sơ thành tích · BXH toàn Server · Chơi tiếp · Giao hữu 1vs1 · Quản trị Server

**Phiên bản tài liệu:** **3.4 — 14/10/2026** (hoàn thiện ba acceptance criteria sau review Codex V3.3: tách tập scoring/Early Close, fence pending ban tại OPEN, và cleanup theo thế hệ membership/reservation; chưa triển khai)  
**Mục đích:** Đặc tả nghiệp vụ và thiết kế tích hợp cho Codex đối chiếu source, lập kế hoạch và triển khai theo giai đoạn; phân biệt rõ luật đã được người dùng xác nhận và các tham số đề xuất.  
**Công nghệ/phạm vi:** Java 21, Spring Boot, Web Client, REST/WebSocket, JPA/MySQL; một Server và một Database, nhiều Client trên LAN.  
**Căn cứ:** `GIOI_THIEU_PROJECT_CHO_NHOM(1).md` (luật gameplay), tài liệu V2/V3/V3.1/V3.2/V3.3, **các đợt review tĩnh source do Codex gửi lại (bao gồm phản biện V3.3)** và quyết định người dùng: ba hình thức xử lý ADMIN, **đăng nhập chung và điều hướng theo role**. Các vị trí/class được Codex nêu là bằng chứng review, **không phải kết quả kiểm thử runtime**; Codex phải xác nhận lại trên working tree hiện tại.  
**Trạng thái:** Đây là **bản thiết kế đề xuất có thông số cụ thể**, **không** phải bằng chứng các chức năng mới đã hoàn thành.

> **Quan trọng:** Bản thiết kế này giữ **luật điểm gameplay hiện hành**. Quiz **có Spin, Hope Star, điểm phạt, các hiệu ứng và giới hạn điểm sàn 0**; sáu chế độ khác có điểm của riêng chúng, bao gồm thưởng câu cuối +20 theo tài liệu hiện tại. Hệ thống danh hiệu **không sửa, không giản lược, không tự tính lại** các quy tắc đó. Nó sử dụng kết quả đúng/sai, thời gian và điểm **đã được Server chốt**. Không sử dụng giả định sai rằng “Quiz luôn đúng +10, sai 0”.

> **NỀN TẢNG V3 – 6 ĐIỂM QUAN TRỌNG GIỮ NGUYÊN:**
> 1. **Giao hữu giữ nguyên toàn bộ luật chơi V2 của trận thường:** tính điểm cộng dồn qua ba màn; **không** tính thắng theo màn, **không** reset điểm, Spin/Star/streak hay tài nguyên giữa màn. Kết quả giao hữu theo **bảng xếp hạng cuối trận do Server tính**.
> 2. Thành tích/cúp/danh hiệu chính thức chỉ xét **Game V2**, `FINISHED` **và** `endReason = COMPLETED`. Trận V1 cũ chỉ giữ History, không bị tự động tính vào hệ thống thưởng mới.
> 3. “Chuyên gia đa tài” là **mốc thành tựu**: *mọi* người đạt chuẩn đều có thể được trao, không phải chỉ người cao nhất.
> 4. Việc **chấp nhận lời mời giao hữu phải giữ chỗ đồng thời cho cả hai tài khoản** đến khi START/hủy/hết hạn; chống nhận nhiều lời mời song song.
> 5. **“Ông vua thách đấu”** là thẻ thành tích giao hữu **riêng** (đề xuất ngưỡng 5 trận giao hữu thắng hoàn thành trước ≥3 đối thủ khác nhau); không tăng cúp/điểm hạng chính thức, không ảnh hưởng gameplay.
> 6. **Room → WAITING → START Game mới là cơ chế đã được Codex báo cáo có trong code**; chỉ hoàn thiện UX, kiểm thử, sửa đúng lỗi nếu phát hiện — không xây lại lifecycle vô cớ.

> **CẬP NHẬT V3.3 — CHÍNH SÁCH ADMIN SAU REVIEW CODEX:** **Một trang đăng nhập chung**, ADMIN được Server xác thực role và chuyển `/admin`. **Kick Game X đề xuất giải phóng `user_active_game` có điều kiện để sang Game Y**; **giữ tập đợi câu đang OPEN, chỉ loại khỏi các câu tiếp theo**; chặn cả ANSWER/RECONNECT/**REST snapshot và WS outbound**; **giữ Score/History nhưng loại người bị Kick/Ban khỏi cúp/danh hiệu của Game đó**; Host bị ban thì Game vẫn chạy, ADMIN có thể đóng **Room WAITING bị bỏ lại**; ban/gỡ ban có `version`, idempotency, chống replay ban cũ và thu hồi toàn bộ phiên. **Đây là phương án thiết kế V3.3 để Codex phản biện, chưa xác nhận đã có code và chưa phải lệnh triển khai**. **Bốn bổ sung bắt buộc:** câu `QUESTION_CLOSED` nhưng chưa `SCORING` vẫn phải được chấm; ban intent gắn đúng thế hệ phiên và Game mục tiêu; cúp yêu cầu `CORRECT` trong *nhóm eligible*; mốc thắng của xử lý ban/thưởng là **commit transaction**, không phải ACK tới Client. Chi tiết và điểm phải duyệt ở mục 18.

> **CẬP NHẬT V3.4 — BA ACCEPTANCE CRITERIA CHỐT ĐỂ REVIEW TRIỂN KHAI (không sửa gameplay):**  
> **(AC-40)** Phân biệt `questionParticipation/scoringSet` (tất cả người phải có outcome, kể cả offline bình thường theo luật V2) và `QuestionCloseGate/earlyCloseWaitSet` (chỉ những người phải chờ để đóng câu sớm). **Không suy diễn tập chấm từ tập chờ.**  
> **(AC-41)** Lúc `QUESTION_OPEN` phải xét trạng thái Account Ban **đã commit** và cả **Game-targeted pending ban intent** đã commit, dù ejection chưa chạy; ban commit trước OPEN thì người đó không được đưa vào tập chấm/không có `NO_ANSWER` câu mới. Thứ tự OPEN–BAN được xác định bởi transaction/fence, không bởi ACK hay thời điểm UI.  
> **(AC-42)** Cleanup Room membership/reservation gắn **đúng thế hệ lượt tham gia (`membershipGeneration`) và `reservationId`**, áp dụng bằng so khớp có điều kiện. RoomMember có thể tái sử dụng row khi rejoin; `(roomId,userId)` không đủ để dọn an toàn. Ban → trì hoãn cleanup → UNBAN → JOIN/RESERVE mới → replay cleanup cũ **không** đụng lượt mới.  
> Ba nội dung này là **yêu cầu an toàn kỹ thuật** đối với chính sách V3.3 đang được đề xuất. Không ngầm khẳng định source hiện tại đã có các field/snapshot/guard tương ứng và **chưa có test nào được chạy trong tài liệu này**. Các ngưỡng thưởng và quy tắc nghiệp vụ khác vẫn theo mức chốt ghi tại từng mục.

**Quy ước về mức chốt:** `ĐÃ CHỐT` = yêu cầu nghiệp vụ người dùng đã xác nhận; `KHUYẾN NGHỊ V3/V3.3` = mặc định đề xuất cụ thể, Codex phải nêu rõ khi lập kế hoạch, không coi là luật đã phê duyệt; `ĐÃ CÓ THEO CODEX` = kết quả khảo sát source trước đây, phải đọc lại code để xác thực; `SAU MVP` = tính năng triển khai ở giai đoạn sau. Văn bản này không tự xác nhận một chức năng đã được code xong.

---

## MỤC LỤC

1. [Mục tiêu và quyết định thiết kế](#1-mục-tiêu-và-quyết-định-thiết-kế)
2. [Nền tảng đã có và phần phải phát triển](#2-nền-tảng-đã-có-và-phần-phải-phát-triển)
3. [Mô hình kết quả ba cấp](#3-mô-hình-kết-quả-ba-cấp)
4. [Luồng trải nghiệm sau trận](#4-luồng-trải-nghiệm-sau-trận)
5. [Quy tắc công nhận cúp và trận hợp lệ](#5-quy-tắc-công-nhận-cúp-và-trận-hợp-lệ)
6. [Danh hiệu phụ và điều kiện trao](#6-danh-hiệu-phụ-và-điều-kiện-trao)
7. [Hồ sơ người chơi và bảng xếp hạng toàn Server](#7-hồ-sơ-người-chơi-và-bảng-xếp-hạng-toàn-server)
8. [Chơi tiếp, tái đấu và thống kê phiên phòng](#8-chơi-tiếp-tái-đấu-và-thống-kê-phiên-phòng)
9. [Thách đấu giao hữu 1vs1](#9-thách-đấu-giao-hữu-1vs1)
10. [Tương tác Client–Server và tính nhất quán dữ liệu](#10-tương-tác-clientserver-và-tính-nhất-quán-dữ-liệu)
11. [Mô hình dữ liệu ở mức khái niệm](#11-mô-hình-dữ-liệu-ở-mức-khái-niệm)
12. [Tình huống biên và quy tắc giải quyết](#12-tình-huống-biên-và-quy-tắc-giải-quyết)
13. [Ví dụ diễn biến hoàn chỉnh](#13-ví-dụ-diễn-biến-hoàn-chỉnh)
14. [Kiểm thử, thực nghiệm và bảo vệ môn Lập trình mạng](#14-kiểm-thử-thực-nghiệm-và-bảo-vệ-môn-lập-trình-mạng)
15. [Phạm vi triển khai theo giai đoạn](#15-phạm-vi-triển-khai-theo-giai-đoạn)
16. [Bảng phân biệt quyết định đã thống nhất và đề xuất](#16-bảng-phân-biệt-quyết-định-đã-thống-nhất-và-đề-xuất)
17. [Hướng dẫn giao tài liệu cho Cow/Codex](#17-hướng-dẫn-giao-tài-liệu-cho-cowcodex)
18. [Quản trị Server V3.4: phân quyền, kick, ban và xử lý đồng thời](#18-quản-trị-server-v34--phân-quyền-kick-ban-và-xử-lý-đồng-thời)

---

# 1. MỤC TIÊU VÀ QUYẾT ĐỊNH THIẾT KẾ

## 1.1. Bài toán và mục tiêu

MultiGame hiện cho phép Host tạo phòng, chọn 1–7 chế độ, các Player cùng chơi một trận V2 có nhiều màn và xem kết quả. Sau trận, cần có lý do để quay lại: **vinh danh người thắng, ghi nhận kỹ năng khác nhau, giữ thành tích theo tài khoản, chơi tiếp trong cùng phòng và giao hữu với bạn bè**.

Mục tiêu kỹ thuật: server xác nhận và lưu thành tích bền vững, chống cấp trùng, đồng bộ giữa nhiều Client qua REST/WebSocket mà không làm thay đổi luật chơi hay tạo thêm yêu cầu Cloud/multi-instance.

## 1.2. Hệ thống sau nâng cấp

- **Trận thường NORMAL:** Kết quả trận có bảng xếp hạng; trận đủ tiêu chuẩn được cộng cúp vào BXH chính thức toàn Server; có danh hiệu phụ.
- **Hồ sơ/Sảnh danh vọng:** Thống kê lâu dài theo tài khoản, tách hoàn toàn khỏi Room và phiên đăng nhập.
- **Chơi tiếp:** Cùng Room có thể tổ chức các GameSession nối tiếp, không cần tạo lại Room hoặc nhập mã mới (đã có nền trong code).
- **Giao hữu FRIENDLY_1V1:** Hai Player thách đấu qua ba chế độ khác nhau, nhưng **toàn bộ gameplay, điểm và tie-break theo luật V2 hiện tại**. Thắng thua chỉ tính ở mục giao hữu, không ảnh hưởng cúp/BXH chính thức.
- **Thẻ “Ông vua thách đấu”:** Vinh danh thành tích giao hữu tích lũy, nằm trong hồ sơ, tách khỏi danh hiệu thắng trận thường.
- **Quản trị ADMIN (cập nhật V3.3):** **Dùng cùng trang đăng nhập với Player**; Server phân luồng ADMIN → `/admin`, Player → lobby. Kick theo Game; Ban tạm/vô thời hạn theo tài khoản; hỗ trợ gỡ cấm, audit và đóng Room WAITING bỏ lại khi Host bị ban. ADMIN không đồng nghĩa Host.

## 1.3. Những điều tuyệt đối không thay đổi — ĐÃ CHỐT

1. **Server-Authoritative:** Server sở hữu câu hỏi, đáp án, deadline, Answer, Score, Ranking, thưởng và trạng thái Room/Game.
2. **Không thay đổi luật từng mode.** Quiz có Spin, Hope Star, điểm phạt, streak và các effect theo tài liệu mới nhất; Quiz V2 có điểm sàn 0. Sáu mode còn lại giữ nguyên luật, kể cả thưởng +20 ở câu cuối màn không phải Quiz theo tài liệu hiện tại.
3. **Không tạo mode thứ tám:** giao hữu là loại trận sử dụng ba trong bảy mode.
4. **Không đổi luật theo kiểu trận:** một lần giao hữu cũng có **một bảng điểm chung** từ 0, cộng dồn qua ba màn giống trận thường; không “best-of-three”/thắng theo màn, không cấp lại tài nguyên hoặc xoá streak khi chuyển màn nếu engine V2 bình thường không làm như vậy.
5. **Giao hữu chỉ để vui:** không cộng/trừ cúp, trận thắng chính thức, tỷ lệ thắng chính thức hoặc điểm xếp hạng toàn Server.
6. **Thưởng không thêm sức mạnh:** cúp, huy hiệu và thẻ danh hiệu không tạo effect/điểm mới trong gameplay.
7. **Giữ nguyên** reconnect, AFK/Early Close V2 đã sửa, deadline, idempotency Answer, transaction, queue theo GameSession, WebSocket outbound FIFO và phân quyền.
8. **Một Spring Boot Server + một MySQL + nhiều Client LAN**; không ép Redis, Kafka, nhiều Server, Elo/MMR hoặc Cloud.
9. Phân hệ thành tích mới chỉ áp dụng cho **gameplay schema V2**, không ghi đè hoặc tự chấm lại lịch sử V1.

## 1.4. Những tham số triển khai V3 khuyến nghị, chưa phải chốt của người dùng

- Cúp chính thức: trận thường V2, `endReason = COMPLETED`, tối thiểu 3 Player/10 câu và **ít nhất một câu đúng trong nhóm eligible tại `awardDecisionCommit`**; khi có ADMIN Kick/Ban áp dụng đầy đủ mục 18.6 (đề xuất, cần duyệt).
- Tay nhanh: đánh giá riêng mỗi mode; ít nhất 5 câu đúng và độ chính xác ≥60%.
- Bách phát bách trúng: 100% câu được quyền trả lời đúng, tối thiểu 10 câu.
- Đa tài: đạt chuẩn ở ≥3 mode, mỗi mode có ≥2 câu đúng và tỷ lệ đúng ≥50%; **tất cả người đạt đều nhận**.
- Giao hữu: ba mode khác nhau, 5 câu/mode mặc định đề xuất; yêu cầu nội dung trung lập và hai Player sẵn sàng.
- “Ông vua thách đấu”: đạt ≥5 trận giao hữu thắng `COMPLETED` trước ≥3 đối thủ phân biệt; cấp thẻ **một lần** và giữ vĩnh viễn; không dùng trận xin thua để tích tiến độ thẻ.
- TTL lời mời 60 giây; thời gian chuẩn bị sau chấp nhận 180 giây, có thể cấu hình nếu phù hợp với UI hiện tại.

Các mặc định này được đưa ra để giảm mơ hồ kỹ thuật; khi Codex review phải liệt kê chúng là **thông số đề xuất cần người dùng duyệt trước bước sửa logic tương ứng**, không tự tuyên bố đã chốt.

## 1.5. Nguyên tắc công bằng vừa đủ

BXH theo cúp là **bảng thành tích tích lũy**, không phải đo trình độ tuyệt đối. Giao hữu không xếp hạng chính thức nên có thể giữ nguyên đặc trưng điểm khác nhau của Quiz và các mode khác mà không xây engine chấm riêng. Nếu người chơi thông đồng hoặc đã thuộc câu hỏi, đó là giới hạn của trò chơi LAN; không hứa hẹn chống gian lận cấp giải đấu.

# 2. NỀN TẢNG ĐÃ CÓ VÀ PHẦN PHẢI PHÁT TRIỂN

## 2.1. Điểm quan trọng từ review Codex mới nhất

Theo **Codex đã đọc source**, không chỉ dựa vào tài liệu cũ:

- Khi một trận kết thúc, `GameTransactions` chốt kết quả `FINISHED`, giải phóng occupancy của tài khoản, rồi **đưa Room trở lại `WAITING` và giữ thành viên phòng**.
- Lần START tiếp theo đã có cơ chế tạo `GameSession`, `PlayerSession` và snapshot câu hỏi mới; điểm của trận mới bắt đầu từ 0.
- Giao diện kết quả đã có **“Quay lại phòng”** trong `game.js`; Host có thể sửa cấu hình và bắt đầu trận mới khi Room đang chờ.
- `GameHistoryService` và cơ sở dữ liệu đã lưu các dữ liệu cần thiết của trận: Answer, outcome, thời gian, chế độ, thứ hạng cuối.
- Codex chưa phát hiện thành phần cúp, trao danh hiệu, bảng thành tích toàn Server hoặc thách đấu 1vs1 trong source đã kiểm tra.
- Codex **chưa chạy kiểm chứng toàn bộ luồng nhiều trận liên tiếp** ở lượt review này.

**Hệ quả thiết kế:** Không được giao Codex “xây lại vòng đời Room để có nhiều trận”. Phải **tận dụng cơ chế Room → WAITING đang tồn tại**, chủ yếu cải thiện nút/luồng UX và bổ sung regression test. Chỉ sửa lifecycle nếu phát hiện lỗi thực tế khi chạy nhiều trận.

Các vị trí Codex đã nêu làm điểm bắt đầu khảo sát (số dòng có thể thay đổi): `GameTransactions.java` khoảng dòng 415, `GameHistoryService.java` khoảng dòng 17, `game.js` khoảng dòng 16.

## 2.2. Bảng đối chiếu

| Hạng mục | Trạng thái theo review Codex | Hướng thực hiện |
|---|---|---|
| Một trận nhiều chế độ | Có | Giữ nguyên |
| Chấm điểm, deadline, scoreboard theo Server | Có | Giữ nguyên |
| Trận FINISHED → Room WAITING, giữ thành viên | Có implementation | Kiểm thử thực tế và hoàn thiện UX |
| START trận mới, điểm từ 0 | Có implementation | Kiểm thử không dùng dữ liệu trận cũ |
| History, Answer, outcome, thời gian, mode | Có | Tận dụng làm nguồn trao danh hiệu |
| Cúp vô địch lưu theo tài khoản | Chưa có bằng chứng | Phát triển |
| Danh hiệu phụ tự động tính từ dữ liệu | Chưa có bằng chứng | Phát triển |
| Bảng xếp hạng toàn Server | Chưa có bằng chứng | Phát triển |
| Thách đấu 1vs1 | Chưa có bằng chứng | Phát triển sau |
| Vai trò ADMIN, trang `/admin`, kick game và cấm tài khoản | Chưa có bằng chứng | **Đọc source xác minh; đặc tả thêm ở mục 18, không tự sửa module khi chưa được giao** |

**Mức độ xác nhận:** Đây là thông tin từ báo cáo khảo sát code của Codex do người dùng chuyển lại, **không phải kết quả tôi trực tiếp chạy source**. Trước mỗi task, Codex phải xác nhận source tại thời điểm thực hiện.

---

# 3. MÔ HÌNH KẾT QUẢ BA CẤP

```text
TOÀN SERVER — AccountAchievements / GlobalLeaderboard
    │ Tổng trận hợp lệ, số cúp, win rate, danh hiệu, thống kê giao hữu riêng
    ▲
PHÒNG — Room (một phiên nhóm bạn chơi cùng nhau)
    │ Trận 1 → Trận 2 → Trận 3 → ... → Host đóng phòng
    ▲
TRẬN — GameSession
    │ Stage 1 → Stage 2 → Stage 3 ... → Result FINISHED
    └ Điểm, Answer, thời gian, thứ hạng riêng của trận
```

## 3.1. Cấp một: Kết quả trận

- Trận thường V2 chọn từ **1 đến 7 chế độ** và tổ chức các màn tuần tự. Trận giao hữu V2 chọn 3 chế độ khác nhau nhưng vẫn dùng chính engine và tie-break này.
- Điểm của các màn được cộng vào **tổng điểm trận** bằng engine hiện tại.
- Thứ hạng: điểm cuối trận giảm dần; nếu bằng điểm, tổng thời gian trả lời **đúng** thấp hơn đứng trên; nếu vẫn bằng thì đồng hạng.
- Điểm/tài nguyên/trạng thái gắn với GameSession không chuyển sang GameSession tiếp theo.
- Game bị hủy/gián đoạn vẫn có History theo trạng thái thật, **không được công bố là trận thắng hợp lệ** cho thành tích. Phải xét `endReason`, không chỉ `status = FINISHED`.

## 3.2. Cấp hai: Thành tích phiên phòng

Một Room có thể chứa nhiều trận nối tiếp, nhưng **mỗi trận vẫn là thực thể độc lập**.

- Sau Trận 1, phòng quay về WAITING; thành viên vẫn ở phòng.
- Thành viên nào không tham gia Trận 2 không được tính là đã chơi Trận 2.
- Trận 2 có thể chọn màn, ngân hàng câu hỏi và thời gian khác Trận 1.
- **Nhà vô địch đương nhiệm của phòng** (nhãn tạm): người thắng trận hoàn thành gần nhất trong phòng.
- **Nhà vô địch phiên phòng** (khi đóng phòng): người có nhiều **trận V2 NORMAL kết thúc `COMPLETED` đứng hạng nhất trong phòng** nhất; nếu hòa, cùng được ghi nhận đồng vô địch phiên. Không ép Host mở thêm chung kết mới được đóng phòng. Trận giao hữu hoặc trận bị hủy không dùng để xác định danh hiệu này.
- Số trận thắng phòng là thành tích thống kê **riêng**, không thay thế cúp toàn Server.

**Khuyến nghị MVP:** Hiển thị bảng thống kê phiên phòng khi cần, nhưng không cần ngay một bảng dữ liệu phức tạp riêng để duy trì điểm cộng dồn mỗi câu qua nhiều trận. Có thể tổng hợp từ các GameSession của Room. Tính năng nhà vô địch phiên phòng có thể được hoàn thiện sau cúp từng trận.

## 3.3. Cấp ba: Thành tích toàn Server

Mỗi tài khoản tích lũy qua nhiều phòng khác nhau:

- Số **trận thường đủ điều kiện** đã hoàn thành.
- Số trận thắng hợp lệ và **số cúp chính thức**.
- Tỷ lệ thắng trên các trận thường đủ điều kiện.
- Số danh hiệu từng loại và lịch sử nhận danh hiệu.
- Thống kê số câu đúng, số câu hợp lệ, hiệu suất theo chế độ (nếu dữ liệu đủ để tổng hợp).
- Thắng/hòa/thua **giao hữu** ở một nhóm thống kê riêng.

Mọi thành tích toàn Server đều **gắn với tài khoản**, không gắn với phiên đăng nhập, một WebSocket hay một Room còn đang mở.

---

# 4. LUỒNG TRẢI NGHIỆM SAU TRẬN

## 4.1. Luồng chuẩn

```text
Câu cuối đóng và Server chấm xong
        ↓
Server commit kết quả GameSession = FINISHED + endReason
        ↓
Xác định trận có đủ điều kiện nhận CÚP CHÍNH THỨC không
        ↓
Tính cúp và danh hiệu từ kết quả đã chốt
        ↓
Lưu thành tích một lần duy nhất, không cộng trùng
        ↓
Phát kết quả / achievement snapshot qua WebSocket
        ↓
┌─────────────────────────────────────┐
│           LỄ VINH DANH              │
│  Hạng 1–2–3 · Cúp · Danh hiệu phụ   │
│  Thành tích mới · Thống kê đáng chú ý│
│ [Quay lại phòng]  [BXH]  [Hồ sơ]    │
└─────────────────────────────────────┘
        ↓
Host cấu hình và START trận mới / Host đóng phòng
```

## 4.2. Nội dung giao diện vinh danh

Gợi ý thứ tự hiển thị để có cảm giác “kết thúc có phần thưởng”:

1. **Bục xếp hạng:** Ba vị trí dẫn đầu, người thắng và tổng điểm chính thức.
2. **Trao cúp:** Nếu đây là trận chính thức đủ điều kiện, hiện “+1 Cúp vô địch” tại tài khoản có quyền nhận.
3. **Danh hiệu phụ:** Các thành tích nổi bật như Tay nhanh, Bách phát bách trúng; nếu không ai đạt thì không ép trao.
4. **Thống kê của bản thân:** Điểm, số câu đúng, thời gian trả lời đúng, vị trí.
5. **Điều hướng:** `Quay lại phòng`, `Xem hồ sơ`, `Xem BXH toàn Server`, `Rời phòng / Về sảnh` theo quyền và trạng thái.

**Không yêu cầu mọi Player cùng bấm “Tiếp tục” để mới chấm hoặc phát thưởng.** Trận phải chốt và thưởng xong độc lập với animation hay nút của Client; người rời mạng vẫn được lưu thành tích.

## 4.3. Nút Chơi tiếp / Tái đấu

Vì code đã có `Quay lại phòng`, **không thiết kế lại lifecycle**. Đề xuất dùng nhãn thân thiện hơn trên giao diện:

- Người chơi thường: `Quay lại phòng` (hoặc `Chơi tiếp`), đưa về WAITING của Room hiện tại.
- Host: `Cấu hình trận tiếp` trong phòng WAITING; kiểm tra người chơi và quyền dùng bộ câu; sau đó `Bắt đầu trận`.
- Người không muốn tiếp tục: `Về sảnh`; khi đang ở WAITING, thao tác Leave theo chính sách Room hiện tại.

Từ “Tái đấu” chỉ là khởi tạo **trận mới**, không tự động phát lại cùng câu hỏi/cùng snapshot, và không reset cúp hoặc lịch sử. Nếu trận mới dùng lại bộ câu, việc người chơi đã nhớ đáp án là vấn đề công bằng nội dung, không phải lỗi đồng bộ Server.

---

# 5. QUY TẮC CÔNG NHẬN CÚP VÀ TRẬN HỢP LỆ

**Vấn đề Codex phát hiện:** Trong source, `status = FINISHED` chưa đủ vì các Game bị hủy hoặc Server gián đoạn cũng có thể mang status này. Vì vậy phần thưởng chính thức **phải kiểm tra đồng thời `schemaVersion = 2`, `gameType = NORMAL`, `status = FINISHED`, `endReason = COMPLETED`** (hoặc các giá trị/field tương đương được Codex xác minh từ schema hiện tại).

## 5.1. Phân biệt kết quả trận và trận được công nhận

- **Kết quả trận:** Game có History, điểm và thứ hạng theo Server; bao gồm cả trận ngắn, miễn kết thúc có dữ liệu để hiển thị.
- **Trận được công nhận cho BXH chính thức:** Chỉ Game **NORMAL V2** đã kết thúc thực sự bằng `COMPLETED` và qua bộ lọc bên dưới mới tích lũy **trận chính thức / trận thắng chính thức / cúp chính thức / danh hiệu lưu hồ sơ chính thức**.
- **Trận V1 lịch sử:** Không tự cấp cúp/thành tích mới, không chỉnh sửa dữ liệu/chính sách loại người hoặc thời gian của V1. Nếu về sau muốn hỗ trợ V1, lập đặc tả riêng.
- **Giao hữu:** Có History và WIN/DRAW/LOSS riêng, nhưng **không thuộc bộ lọc cúp chính thức**, bất kể số câu hoặc kết quả.

## 5.2. Bộ điều kiện MVP — KHUYẾN NGHỊ V3

| Kiểm tra bắt buộc | Giá trị/điều kiện |
|---|---|
| Version | Game V2 (`schemaVersion = 2` hoặc field tương đương) |
| Loại | `NORMAL` |
| Trạng thái và lý do | `FINISHED` **và** `endReason = COMPLETED` |
| Roster | ≥3 Player hợp lệ trong snapshot trận, không tính Spectator |
| Độ dài | ≥10 câu **đã cấu hình và thực sự diễn ra trong Game hoàn tất**; không chỉ đếm câu của ngân hàng |
| Dữ liệu hợp lệ | Kết quả, Answer outcome và final ranking đã commit, không ở trạng thái lỗi hoặc chưa finalize |
| Có thành tích thi đấu | **≥1 Answer outcome `CORRECT` của một Player thuộc tập `eligibleForOfficialAwards` được khóa/chốt trong award transaction**; không tính câu đúng của người đã bị loại do ADMIN |
| Idempotency | Có khóa chống thưởng trùng, không thể cộng hai lần do retry/event gửi lại |
| **Có ADMIN can thiệp** (KHUYẾN NGHỊ V3.3) | Snapshot đầu ≥3 Player; tại **transaction chốt award** còn **≥2 Player eligible** và nhóm này có ≥1 `CORRECT`; không sửa Score/Ranking lịch sử (xem mục 18.6, 18.10 và 18.15) |

Các điều kiện phải được kiểm tra **ở Server**, snapshot theo Game và lưu/có thể tái dựng minh bạch; không lấy từ số câu Client tự khai. Trận 1–9 câu vẫn chơi và có History/bảng kết quả, nhưng không cộng cúp hoặc số trận chính thức; UI cần báo trước khi START. Ngưỡng 10 câu là khuyến nghị, không phải quyết định luật đã được người dùng xác nhận.

## 5.3. Cấp cúp, hòa và tích lũy

- **Trận không có ADMIN can thiệp:** các Player **đồng hạng 1** theo `RankingEngine` V2 đều nhận **mỗi người +1 cúp** nếu Game đủ điều kiện; SPECTATOR không có cúp. **Trận có người bị ADMIN Kick/Ban:** dùng thứ hạng cao nhất **trong nhóm Player còn eligible tại award commit**, có thể đồng nhận; giữ nguyên final ranking đã lưu và giải thích award rank khác final rank ở UI (mục 18.6).
- Đối với tỷ lệ thắng chính thức: mỗi người đồng hạng ở vị trí **được trao cúp hợp lệ** được tính một thắng trên cùng Game đủ điều kiện; chỉ PLAYER **không bị loại vì ADMIN** mới tăng một trận tham gia chính thức. Trận không đủ điều kiện thưởng không tăng mẫu số cho ai (mục 18.6).
- Game không đủ điều kiện (kể cả status FINISHED nhưng endReason khác COMPLETED) **không** tăng tử/mẫu tỷ lệ chính thức. Có thể hiển thị lịch sử mọi Game riêng.
- Khi đóng Room hoặc tái đấu, không cấp lại cúp cũ. Một lần kết thúc chỉ sinh bản ghi thưởng tương ứng **một lần**.

## 5.4. Công bằng và nguồn dữ liệu

Tổng điểm giữa các GameSession không có thang đồng nhất vì số câu, chế độ, Spin/Star và thưởng câu cuối khác nhau. Vì vậy BXH toàn Server **không lấy tổng điểm các câu qua mọi trận** mà xếp theo cúp rồi các tỷ lệ (mục 7).

Cúp không chứng minh trình độ tuyệt đối: 3 Player/10 câu và chính sách đúng ≥1 chỉ chống các trường hợp dễ farm nhất. Với project LAN, không thêm Elo/MMR hoặc phát hiện thông đồng quá mức. Nếu sau này đổi ngưỡng, không được âm thầm thu hồi hay cấp lại cúp lịch sử mà không có quy trình/version rõ ràng.

# 6. DANH HIỆU PHỤ VÀ ĐIỀU KIỆN TRAO

Danh hiệu được Server tính từ **Answer outcome và thời điểm được ghi nhận**, không đoán theo điểm cộng/trừ. Tất cả danh hiệu là **thành tích**, không phải effect tác động luật chơi.

## 6.1. Danh hiệu đề xuất

| Danh hiệu | Ý nghĩa | Điều kiện đề xuất | Mức ưu tiên |
|---|---|---|---|
| **Tay nhanh – [tên chế độ]** | Trả lời đúng nhanh trong **cùng một chế độ** | Ít nhất **5 câu đúng trong chế độ**, tỷ lệ đúng chế độ **≥60%**; thời gian trung bình trên câu đúng thấp nhất giữa người đủ điều kiện | **MVP** |
| **Bách phát bách trúng** | Không mắc lỗi trong cả trận | Đúng **100% số câu có quyền tham gia** và ít nhất **10 câu** | **MVP** |
| **Chuyên gia đa tài** | Mốc thành tựu đa chế độ cho **mọi** Player đạt chuẩn | Đạt chuẩn ở ít nhất **3 chế độ**; mỗi chế độ ≥2 câu đúng và ≥50% câu có quyền chơi | Mở rộng |
| **Lội ngược dòng** | Từ cuối bảng vươn lên vô địch | Trận có ≥2 màn; người đứng **cuối duy nhất** sau màn đầu và đứng **đầu duy nhất** lúc kết thúc | Mở rộng |

Cúp vô địch là **phần thưởng chính**, không gộp vào bốn danh hiệu phụ trên. Không nhất thiết trận nào cũng có đủ danh hiệu; nếu không ai đạt ngưỡng, hiển thị “không có người đạt”.

## 6.2. Lý do thay đổi hai định nghĩa ban đầu

**Tay nhanh:** Không so thời gian của câu Quiz với câu Đoán bài hát hoặc Truy tìm dấu vết như thể chúng tương đương. Chúng có đặc điểm video/gợi ý và khoảng thời gian khác nhau. Thay vào đó **xét riêng từng chế độ**, bảo đảm người nhận không chỉ đúng một câu rất nhanh mà bỏ các câu còn lại.

**Bách phát bách trúng:** Nếu chỉ gọi người “có tỷ lệ cao nhất”, một người đúng 60% cũng có thể được trao danh hiệu mang tên “bách phát bách trúng”. Vì vậy chọn **100%**, có ngưỡng tối thiểu; tránh sai lệch giữa tên và điều kiện.

## 6.3. Quy tắc mẫu số và thời gian

- Một câu được đưa vào mẫu số của Player khi họ là PLAYER **có quyền trả lời câu ấy** trong GameSession theo roster/quyền Server, kể cả đang offline và được chấm `NO_ANSWER`.
- **Không tính** câu của trận khác, SPECTATOR, câu chưa từng mở, hoặc câu của Game bị CANCELLED/INTERRUPTED vào danh hiệu trận hoàn thành.
- Tỷ lệ đúng = `số câu có outcome CORRECT / số câu thuộc tập tính của Player`.
- Với Tay nhanh: thời gian = `Server nhận Answer hợp lệ - mốc Server mở câu` theo dữ liệu đã lưu; chỉ lấy các Answer **đúng**; sai và bỏ trống không tính thời gian trung bình, nhưng vẫn tác động đến ngưỡng tỷ lệ đúng.
- Không tin thời gian do Client tự khai; không tính thời gian intro, decision phase, thời gian tải video trước câu hoặc chờ kết quả.
- Nếu một người đáp ứng nhiều danh hiệu, họ có thể nhận nhiều; nếu nhiều người đồng chỉ số đứng đầu, **đồng nhận** khi cùng đạt ngưỡng. Không tự chọn theo tên tài khoản.

## 6.4. Cách đánh giá theo từng danh hiệu

### A. Tay nhanh – theo chế độ

Ví dụ trận có 6 câu Quiz: A đúng 5/6, thời gian đúng trung bình 3,2s; B đúng 6/6, trung bình 4,1s; C đúng 2/6, trung bình 1,2s. **A** được Tay nhanh – Quiz (vì C không đạt ngưỡng 5 câu đúng), dù B có số câu đúng cao hơn. Tất cả thời gian lấy từ Server.

Nếu màn chỉ có 3 câu thì **không trao Tay nhanh cho màn đó**. Đây là chủ đích tránh thưởng từ mẫu quá nhỏ, không phải lỗi của game.

Nếu một trận có hai màn cùng một chế độ (nếu cấu hình về sau cho phép), nên gộp các câu theo mode trong cùng GameSession; không ngầm trao danh hiệu hai lần. Code hiện có thể chỉ cho phép mỗi chế độ xuất hiện một lần, cần tôn trọng ràng buộc thực tế.

### B. Bách phát bách trúng

A tham gia 10 câu, đúng 10: **đạt**. B tham gia 10 câu, đúng 9, bỏ 1: **không đạt**. Không dùng điểm 100/120 của Quiz có effect để suy ra số câu đúng.

### C. Chuyên gia đa tài — MỐC THÀNH TỰU, KHÔNG CHỌN MỘT NGƯỜI NHẤT

**Tất cả Player nào** đạt chuẩn tại ≥3 chế độ trong cùng một Game V2 đều được trao, không giới hạn số người nhận. Mỗi chế độ tính là đạt khi có ≥2 câu trả lời đúng và tỷ lệ đúng trong chế độ ≥50% trên các câu được quyền chơi; các chế độ phải có mặt và đủ câu trong snapshot của Game.

Ví dụ A đạt chuẩn ở Quiz/Đố mẹo/Vua Tiếng Việt, B đạt chuẩn ở Quiz/Bài hát/Sắp xếp trình tự: **cả A và B đều được nhận**. C chỉ đạt 2 chế độ: không nhận. Đây là mốc thành tựu độc lập với hạng nhất hoặc so người chơi nào giỏi hơn; không tăng cúp. Danh hiệu có thể không xuất hiện ở trận chỉ chọn 1–2 chế độ.

### D. Lội ngược dòng

Sau khi kết thúc màn đầu, A đứng cuối **không đồng hạng**. Kết thúc toàn trận, A đứng nhất **không đồng hạng** thì nhận. Cần có dữ liệu chụp/khôi phục thứ hạng cuối màn đầu; **ưu tiên tính lại từ lịch sử được lưu** thay vì thêm bảng snapshot mới nếu dữ liệu hiện tại đủ chính xác.

## 6.5. Quy tắc trao và hiển thị

- **Danh hiệu của trận thường:** Chỉ *lưu trong hồ sơ chính thức* khi Game V2 NORMAL có `status = FINISHED`, `endReason = COMPLETED` và đạt điều kiện ở mục 5. Trận ngắn có thể hiển thị chỉ số vui trong màn kết quả nhưng **không hiện thẻ chính thức đã sở hữu**. Trận bị hủy/gián đoạn không trao danh hiệu hoàn thành.
- **Danh hiệu không được trao cho trận hủy hoặc gián đoạn** như thể đã có kết quả đầy đủ.
- Mỗi danh hiệu lưu `type`, `gameId`, `playerId`, chế độ (nếu có), chỉ số đạt được và thời điểm nhận; tránh lưu mỗi một chuỗi chữ tự do.
- Phiên bản đầu chỉ cần **Cúp, Tay nhanh theo chế độ, Bách phát bách trúng**; hai danh hiệu còn lại triển khai khi đã kiểm chứng dữ liệu và UX.

## 6.6. Danh hiệu chuyên biệt của bảy chế độ — định hướng sau

Có thể bổ sung “Tai vàng” (bài hát), “Bậc thầy ngôn ngữ” (Vua Tiếng Việt), “Thám tử tài ba” (Truy tìm dấu vết) ... nhưng cần tiêu chí định lượng công bằng. **Chưa triển khai cả bảy loại ngay** vì dễ biến phần vinh danh thành quá nhiều huy hiệu ít giá trị. Ưu tiên làm tốt 2–4 danh hiệu có dữ liệu rõ ràng trước.

---

# 7. HỒ SƠ NGƯỜI CHƠI VÀ BẢNG XẾP HẠNG TOÀN SERVER

## 7.1. Hồ sơ cá nhân

Đề xuất màn **Hồ sơ thành tích** gồm:

| Nhóm | Dữ liệu |
|---|---|
| Danh tính | Tên hiển thị / tài khoản được phép công khai |
| Thành tích chính thức | Tổng trận thường đủ điều kiện, số lần thắng, số cúp, tỷ lệ thắng |
| Danh hiệu | Loại danh hiệu, số lần đạt, trận nào đạt và lần đạt gần nhất |
| Hiệu suất | Tổng số câu đúng/tổng câu được tham gia; thống kê theo từng mode nếu dữ liệu đủ |
| Lịch sử | Các trận vừa chơi, Room, kết quả và ngày hoàn thành |
| Giao hữu | Thắng/hòa/thua, số trận hoàn thành, số đối thủ riêng biệt, tiến độ/thẻ **Ông vua thách đấu** ở khu vực riêng |

Các số liệu phải lấy từ Database hoặc projection đáng tin cậy trên Server, **không lấy từ localStorage**. Sau khi đóng Room và logout/login, người chơi vẫn xem được.

Có thể cho Player chọn một danh hiệu đã sở hữu hiển thị cạnh tên; đây là **UI mở rộng**, không cần cho MVP.

## 7.2. Bảng xếp hạng toàn Server — đề xuất cách xếp

**Bảng chính:** *Sảnh danh vọng — Thành tích tích lũy*.

Thứ tự sắp xếp đề xuất:

1. **Số cúp chính thức giảm dần**.
2. Nếu bằng cúp: **tỷ lệ thắng trên trận đủ điều kiện giảm dần**.
3. Nếu vẫn bằng: **tỷ lệ câu đúng trên các trận đủ điều kiện giảm dần**.
4. Nếu tất cả chỉ số xếp hạng bằng nhau: **đồng hạng**. Chỉ dùng tên hoặc ID để hiển thị ổn định, không tính là phá hòa về thành tích.

Ví dụ (chỉ minh họa):

| Hạng | Player | Cúp | Thắng / Trận đủ điều kiện | Tỷ lệ thắng |
|---:|---|---:|---:|---:|
| 1 | A | 9 | 9 / 15 | 60,0% |
| 2 | B | 7 | 7 / 10 | 70,0% |
| 3 | C | 4 | 4 / 5 | 80,0% |
| 4 | D | 2 | 2 / 8 | 25,0% |

Tỷ lệ thắng cao không đẩy C vượt B vì **cúp là tiêu chí chính**. Đây là bảng thành tích tích lũy, không phải đo trình độ Elo.

**Tính tỷ lệ thắng:** Khi có đồng vô địch hạng 1, mỗi người được tính một trận thắng, nên tỷ lệ thắng vẫn = thắng / trận đủ điều kiện, tối đa 100%. Khi chưa có trận đủ điều kiện, hiển thị `Chưa xếp hạng`, không chia cho 0.

**Dữ liệu công khai:** Không công khai mật khẩu, thông tin phiên đăng nhập, email riêng tư hoặc đáp án chưa công bố. Chỉ xuất thông tin thống kê cần cho sảnh danh vọng.

## 7.3. Hai bảng riêng, không trộn nhau

- **BXH chính thức**: phòng thường đủ điều kiện; cúp, thắng, tỷ lệ thắng. Mặc định hiển thị ở sảnh chính.
- **Thống kê giao hữu**: số trận giao hữu, thắng/hòa/thua, đối thủ gần đây, thẻ **Ông vua thách đấu** (nếu đạt); mọi chỉ số **tách khỏi cúp/trận chính thức và khóa xếp hạng chính**. Có thể có tab “Giao hữu” nhưng không tính số trận giao hữu vào mẫu số tỷ lệ thắng chính thức.

**Không cần một “điểm hạng” cộng/trừ cố định như +20/−10 lúc này.** Đó là một hệ thống thứ ba cần cơ chế chống farm và hiệu chỉnh độ mạnh, vượt quá phạm vi cần thiết.

## 7.4. Cập nhật bảng xếp hạng

- Kết quả kết thúc trận được commit thành công → phần thưởng lưu thành công → BXH phản ánh bản mới.
- Nhiều Client đang ở sảnh có thể tải lại qua REST; nếu đã có kênh thông báo phù hợp thì Server có thể phát event cập nhật. **Không cần push toàn bộ bảng hạng sau từng Answer.**
- Khi reconnect, Client tải lại hồ sơ/BXH chính thức; không dựa trên event cũ đã mất.
- Việc thay tên tài khoản không làm thay đổi ID của thành tích.

---

# 8. CHƠI TIẾP, TÁI ĐẤU VÀ THỐNG KÊ PHIÊN PHÒNG

## 8.1. Luồng hiện có nên tận dụng

Theo Codex: `Game FINISHED → Room WAITING → giữ thành viên → Host có thể cấu hình lại và START một GameSession mới`. Giao diện hiện có `Quay lại phòng`.

**Vì vậy hạng mục Chơi tiếp là “hoàn thiện luồng”, không phải “xây lại từ đầu”.** Cần kiểm thử trước khi quyết định thay đổi Service hoặc trạng thái Room.

## 8.2. Ví dụ phòng có ba trận

```text
Phòng ABC123 (Host H, Player A/B/C)
    ├── Game 101: Quiz → Đố mẹo
    │      FINISHED → Cúp A → Vinh danh → Room WAITING
    ├── Game 102: Bài hát → Vua Tiếng Việt
    │      FINISHED → Cúp C → Vinh danh → Room WAITING
    ├── Game 103: Quiz → Đuổi hình bắt chữ
    │      FINISHED → Cúp A → Vinh danh → Room WAITING
    └── Host đóng Room ở WAITING
           → Tổng kết phiên: A có 2 trận thắng, C có 1
           → Thành tích toàn Server vẫn được giữ
```

**Điểm đầu mỗi GameSession mới = 0.** `Game 102` không mang điểm, Answer, hiệu ứng Quiz hoặc deadline của `Game 101` sang.

## 8.3. Quyền sau trận

- **Host** giữ quyền cấu hình Room và START trận tiếp theo theo policy hiện tại. Nếu Host là Spectator, vẫn có quyền quản trị Room nhưng không nhận điểm hoặc cúp của trận đang xem.
- **Player thường** có thể xem vinh danh, trở về Room WAITING, đổi vai trò khi chính sách cho phép hoặc rời Room.
- Host đổi bộ câu ở lần START mới thì phải **kiểm tra lại quyền tác giả và điều kiện PLAYER**, vì người chơi hợp lệ ở Game trước có thể là tác giả bộ câu ở Game sau.
- Nếu không đủ số PLAYER cho trận thường, Server vẫn từ chối START theo điều kiện hiện hành; **không tự chuyển sang thách đấu 1vs1**.
- Nếu Host offline trong WAITING, thành viên vẫn có thể xem thành tích và rời phòng; việc chuyển Host là tính năng riêng chưa được chốt, **không tự xây mới**.

## 8.4. Tổng kết phiên phòng khi Host đóng

**Đề xuất giai đoạn sau:** Trước hoặc sau thao tác đóng Room ở WAITING, Server tổng hợp các GameSession **V2 đã `FINISHED` và `endReason = COMPLETED`** của Room để công bố (không tính CANCELLED/SERVER_INTERRUPTED vào số thắng):

- Trận đã chơi trong phiên, người thắng từng trận.
- Thắng/trận của từng Player trong Room.
- Nhà vô địch đương nhiệm (người thắng **Game V2 COMPLETED gần nhất**, nếu đồng hạng thì đồng đương nhiệm).
- Nhà vô địch phiên phòng (nhiều trận thắng nhất trong Room; nếu bằng thì đồng vô địch).
- Một vài điểm sáng: người nhiều câu đúng nhất, người chơi nhiều trận nhất (chỉ là thống kê vui).

**Chốt ranh giới:** Danh hiệu vô địch *phiên phòng* không tự thưởng thêm một cúp toàn Server. **Cúp chính thức chỉ tính theo GameSession đủ điều kiện**, tránh cộng hai lần khi một Room đóng.

## 8.5. Đóng Room không phải logout hoặc ban

Đóng Room chỉ kết thúc phiên nhóm chơi, giải phóng quyền tham gia Room và đưa người chơi về sảnh. **Không đăng xuất toàn bộ tài khoản, không xóa History, không xóa cúp/danh hiệu toàn Server**. Ban/kick chỉ thực thi qua phân hệ ADMIN và **không đồng nghĩa thao tác đóng Room thường**; Room WAITING có Host bị ban có thể được ADMIN đóng theo quyền hạn hẹp ở mục 18.5.2.

---

# 9. THÁCH ĐẤU GIAO HỮU 1VS1 — LUẬT GAMEPLAY V2 KHÔNG THAY ĐỔI

## 9.1. Mục tiêu và phân biệt với phòng thường

**ĐÃ CHỐT:** Giao hữu là nơi hai tài khoản thách đấu vui vẻ; một Player chọn mode sở trường của mình, Player còn lại chọn mode khác, **Server chọn mode thứ ba khác cả hai**. Hai người cùng chơi ba màn trên một trận giao hữu. Trận giao hữu **không cộng/trừ cúp, điểm hạng hay tỷ lệ thắng chính thức** và không thay luật các mode. Thay vào đó lưu **WIN/DRAW/LOSS giao hữu** và có thể trao **thẻ “Ông vua thách đấu”** trong hồ sơ thành tích giao hữu.

Điều kiện `≥3 PLAYER` của trận thường vẫn nguyên vẹn. Giao hữu là **loại trận riêng có 2 Player** và quy tắc lời mời/chấp nhận; không được giảm toàn bộ check START trên mọi Room xuống 2 Player.

## 9.2. LUẬT CỐT LÕI — GIỮ NGUYÊN ENGINE V2

Đây là điểm cần Codex tuyệt đối tuân thủ để tránh sai so với yêu cầu người dùng:

1. **Chung một bảng điểm xuyên suốt ba màn**. Trận bắt đầu cả hai người ở 0; chuyển màn **không reset điểm** và không tạo bảng điểm thắng màn độc lập.
2. **Thứ hạng và người thắng giao hữu xác định từ kết quả cuối GameSession** theo đúng `RankingEngine`/tie-break V2: tổng điểm giảm dần, nếu bằng xét tổng thời gian trả lời **đúng** tăng dần; nếu vẫn bằng là **hòa**.
3. **Quiz dùng nguyên công thức ScoreEngine V2**, gồm điểm phạt, sàn 0, Spin/Hope Star, các effect, Momentum/Recovery, giới hạn lượt và các phase Quiz. Không thêm Spin, Star, giảm phạt hoặc thay đổi luật chỉ vì có hai Player.
4. **Các mode ngoài Quiz dùng nguyên công thức V2** và thưởng câu cuối +20 theo dữ liệu/rules snapshot hiện tại. Không sửa thưởng để cân bằng ba màn.
5. **Tài nguyên, streak, Hope Star và trạng thái ảnh hưởng gameplay sống theo đúng vòng đời V2 hiện tại**, chỉ reset khi bắt đầu GameSession mới như trận thường; không làm mới riêng ở đầu màn hoặc tính lại khi kết thúc màn.
6. Mỗi người nộp một Answer hợp lệ cho mỗi câu; ACK không tiết lộ đúng/sai trước khi đóng câu; Server điều khiển timer, Early Close AFK, Result và reconnect giống trận thường.
7. **KHÔNG** áp dụng “best of three”, “ai thắng hai màn thì thắng”, “mỗi màn về 0 điểm”, “tính điểm màn bằng điểm cuối trừ điểm đầu”, hoặc ScoreEngine giao hữu viết lại riêng.

**Lý do:** Codex cảnh báo chính xác rằng Quiz sàn 0 khiến phép trừ điểm tích lũy để tìm điểm màn có thể méo. V3 **loại bỏ hẳn khái niệm thắng theo màn** nên không cần giải quyết bài toán đó, không tăng phức tạp engine và giữ trọn ý người dùng: *giao hữu luật vẫn như thế*.

Hệ quả về trải nghiệm: một mode có thể tạo nhiều điểm hơn mode khác; **đây là chênh lệch vốn có của game nhiều chế độ**, không phải lỗi của giao hữu. Có thể hiển thị thống kê mỗi màn để người chơi hiểu kết quả, nhưng kết quả trận **chỉ theo ranking chung**.

## 9.3. Luồng giao hữu

```text
A đăng nhập / ở sảnh → chọn tài khoản B → Gửi lời mời
                  ↓ Server kiểm tra identity, quyền, khả dụng
B: Chấp nhận / Từ chối / Lời mời hết hạn
                  ↓ Chấp nhận: GIỮ CHỖ A và B NGAY (atomic)
A chọn mode #1 → B chọn mode #2 khác #1
                  ↓
Server lọc nguồn câu hợp lệ và tự chọn mode #3 khác cả hai
                  ↓
Hai Player xem cấu hình, cùng bấm Sẵn sàng
                  ↓
Server Start FRIENDLY_1V1, snapshot Game V2, điểm 0
                  ↓
Màn 1 → Màn 2 → Màn 3 (điểm liên tục, luật nguyên gốc)
                  ↓
Game endReason COMPLETED → ranking chung → WIN/DRAW/LOSS giao hữu
                  ↓
Lưu kết quả giao hữu & xét thẻ Ông vua thách đấu
                  ↓
Vinh danh giao hữu → Tái đấu nếu cả hai đồng ý / Về sảnh
```

## 9.4. Cấu hình giao hữu — KHUYẾN NGHỊ V3

| Thuộc tính | Đề xuất | Ranh giới |
|---|---|---|
| Số người | Đúng 2 PLAYER | Không mở cơ chế bắt đầu 2 người cho NORMAL |
| Số màn | 3 màn với 3 mode khác nhau | Không tạo mode thứ tám |
| Mode 1, 2 | A chọn mode 1; B chọn mode 2 khác mode 1 | Nếu trùng, B phải chọn lại; không tự thay lựa chọn của B |
| Mode 3 | Server chọn ngẫu nhiên từ các mode còn lại **có đủ ngân hàng hợp lệ** | Không do Client chỉ định, không để lộ đáp án |
| Số câu | Mặc định **5 câu mỗi màn**; đây là tham số đề xuất, phải tương thích validate hiện tại | Nếu mode Quiz chỉ có 5 câu, **0 Spin** theo công thức hiện tại; có thể cấu hình 10 câu Quiz để có Spin nếu người dùng duyệt việc chỉnh số câu, không sửa công thức Spin |
| Thời gian | Áp dụng giới hạn hiện hành theo mode, cùng deadline cho A/B | Không cho Player tự nới thời gian |
| Start | Cả hai xác nhận sẵn sàng; Server Start khi điều kiện thỏa | Không cần Host ra lệnh mở câu theo từng phase |
| Kết quả | **Bảng điểm tích lũy V2** và tie-break V2 | Không xét thắng theo màn |
| Lời mời | Hết hạn sau 60 giây nếu chưa nhận | Tham số khuyến nghị, có thể cấu hình |
| Chuẩn bị | Hết hạn sau 180 giây nếu chưa Start | Giải phóng reservation, không ghi WIN/LOSS |
| Thưởng | Không cúp chính thức/BXH chính thức | Chỉ thống kê giao hữu và xét thẻ riêng |

**Ghi chú Spin:** Với số câu Quiz của trận giao hữu là 5, công thức `floor(totalQuizQuestions/10)` cho 0 Spin; điều này là *hành vi đúng luật gốc*. Nếu muốn trình diễn Spin trong giao hữu, tăng **số câu Quiz lên 10 theo cấu hình hợp lệ**, không hack luật cấp Spin. Hope Star/streak dùng đúng điều kiện hiện hành.

## 9.5. Ngân hàng câu hỏi và tính trung lập

- Bất kỳ ngân hàng được dùng phải có đủ câu/loại media theo mode; kiểm tra trước khi nhận cấu hình và Start.
- **Không dùng bộ câu do A hoặc B làm tác giả**, vì code hiện có policy ngăn tác giả làm PLAYER với bộ câu của mình. Chọn từ nguồn hợp lệ (PUBLIC/được quản trị chuẩn bị) mà cả hai không sở hữu.
- PUBLIC không đảm bảo hai người chưa từng chơi; giao hữu chỉ cần trung lập hợp lý, không quảng bá là anti-cheat tuyệt đối.
- Server lọc các mode còn lại theo *số câu thực có thể snapshot*; nếu không đủ một mode thứ ba hợp lệ, từ chối Start có lý do, không âm thầm lặp mode.
- Không gửi `correctOption`, `acceptedAnswers`, `correctOrder` hoặc clue chưa mở cho Client đang chơi.
- Bộ câu và luật phải được snapshot lúc Start; tác giả chỉnh nội dung sau Start không làm trận giao hữu đổi câu giữa chừng.

## 9.6. Reservation: chấp nhận lời mời là giữ chỗ **cho cả hai**

**Vấn đề Codex phát hiện:** Kiểm tra `ACTIVE_GAME` ở lúc START thôi chưa đủ; sau khi B đồng ý, tài khoản vẫn có thể đồng ý thêm lời mời khác trước khi Start, hoặc A đang chuẩn bị lại Start trận khác. Cần cơ chế giữ chỗ trên Server, không dựa vào các nút bị ẩn ở Frontend.

Quy tắc đề xuất:

1. Lời mời ở `PENDING` **chưa giữ chỗ lâu dài**; có TTL và người gửi/người nhận rõ ràng. Nên giới hạn số lời mời đang chờ để tránh spam.
2. Khi B bấm `ACCEPT`, Server **kiểm tra và reserve đồng thời A và B trong cùng một thao tác nguyên tử**. Nếu bất kỳ người nào đã trong Game/phiên giao hữu chuẩn bị không tương thích, `ACCEPT` bị từ chối; không tạo trạng thái giữ chỗ một người.
3. Mỗi tài khoản chỉ có **tối đa một `FRIENDLY_PREPARING` hoặc `FRIENDLY_ACTIVE`**. Lời mời pending khác gửi tới cùng tài khoản bị hủy/không còn được chấp nhận khi đã reserve.
4. Reservation phải tương thích hoặc tích hợp với occupancy/permission hiện có: kiểm tra cả việc START Game thường trong cùng khoảng thời gian, để không thể vừa reserve giao hữu vừa START trận thường. Trường hợp tài khoản còn membership ở Room WAITING phải có rule rõ trong implementation (từ chối hoặc yêu cầu rời phòng trước), không tự ý xóa membership.
5. Mỗi phiên reserve có **`reservationId` riêng**, trạng thái/version và danh tính hai người được lưu bền vững; `RELEASE/EXPIRE/CANCEL` dùng ID/version đó, không xóa mù tất cả reservation của user trong tác vụ cũ (AC-42). Reserve duy trì qua reconnect, không reset deadline chuẩn bị. Nếu từ chối/hủy/hết hạn/không Start hoặc kết thúc trận, giải phóng một cách idempotent; tránh giữ tài khoản kẹt sau DB error hoặc Server restart.
6. Nếu hai request `ACCEPT` cạnh tranh, chỉ một cặp được reserve và tiến tới chuẩn bị. Dùng transaction/khóa ràng buộc phù hợp thay vì chỉ `if` trên Frontend.
7. `START` chuyển trạng thái reservation sang trạng thái đang thi đấu một cách nhất quán; không có khoảng hở cho hai Game đồng thời đối với cùng một User.

**Trạng thái gợi ý** (không bắt buộc tạo bảng riêng nếu code hiện tại có thể tái sử dụng an toàn):

`PENDING → ACCEPTED/RESERVED → SELECTING → READY → ACTIVE → COMPLETED`

Nhánh kết thúc khác: `REJECTED`, `INVITE_EXPIRED`, `CANCELLED_BEFORE_START`, `PREPARATION_EXPIRED`, `FORFEIT`, `SERVER_INTERRUPTED` — tên chính xác phải đối chiếu enum/schema hiện có.

## 9.7. Xin thua, mất kết nối, kết quả và tái đấu

- **Trước Start:** một trong hai được hủy/chấm dứt chuẩn bị; không tính thắng/thua, giải phóng chỗ.
- **Sau Start:** đề xuất nút **Xin thua (FORFEIT)** để một người chủ động dừng giao hữu. Server kết thúc, ghi một WIN/LOSS giao hữu cho cặp; **không cấp cúp chính thức**. Cần command/server auth idempotent, race với end timer được phân định đúng một kết quả.
- Mất Wi-Fi/đóng tab không tự động là xin thua; áp dụng reconnect và deadline V2, không thêm giờ. Trận tự tiến khi còn điều kiện; nếu Server gián đoạn thì `SERVER_INTERRUPTED`/`NO_CONTEST`, không ghi WIN/DRAW/LOSS từ một trận chưa hoàn tất.
- **Kết thúc tự nhiên:** nếu Game có `status = FINISHED` và `endReason = COMPLETED` **và không bị can thiệp ADMIN loại Player**, xác định thứ hạng theo tổng điểm/tổng thời gian đúng của Game V2. A đứng trên B → A WIN, B LOSS; B đứng trên A → ngược lại; bằng cả hai tiêu chí → cả hai DRAW. Nếu có ADMIN Kick/Ban tác động vào giao hữu, không được ghi một WIN `COMPLETED` để farm thẻ (mục 18.6).
- **Kết thúc FORFEIT:** lưu WIN/LOSS theo người xin thua và đối thủ; record phải phân biệt `FORFEIT` với `COMPLETED`. Nếu xử lý chưa an toàn, ưu tiên chỉ triển khai hoàn tất tự nhiên trước, không âm thầm coi Client disconnect là FORFEIT.
- **Tái đấu giao hữu:** cả hai phải cùng đồng ý; tạo GameSession mới với điểm/tài nguyên/Answer mới, không cộng kết quả cũ lại. Không tự động accept lời mời hoặc giữ reservation vô hạn.

## 9.8. THẺ VINH DANH “ÔNG VUA THÁCH ĐẤU”

### Mục đích — ĐÃ CHỐT VỀ Ý TƯỞNG

Người chơi muốn có một **thẻ thành tích giao hữu** tên **Ông vua thách đấu**. Thẻ nhằm ghi nhận người tích cực chiến thắng giao hữu; là danh hiệu **lâu dài trong hồ sơ** chứ **không phải vật phẩm/gameplay effect** và không tăng thứ hạng toàn Server chính thức. Người có thẻ có thể hiển thị bên cạnh tên trong sảnh hoặc hồ sơ (UI mở rộng).

### Cách mở khóa — KHUYẾN NGHỊ V3

- Có **ít nhất 5 trận `FRIENDLY_1V1` thắng**, kết thúc tự nhiên với `endReason = COMPLETED`.
- Các trận thắng này phải đến từ **ít nhất 3 đối thủ khác nhau** (theo User ID, không theo nickname), tránh chỉ đấu qua lại một người để lấy thẻ.
- Trận hòa, thua, bị hủy, gián đoạn hoặc **thắng do đối thủ xin thua** không tính vào tiến độ thẻ; riêng WIN/LOSS giao hữu vẫn được lưu đúng loại kết quả nếu FORFEIT được hỗ trợ.
- Thẻ chỉ **mở khóa một lần cho mỗi User**, không cộng +1 thẻ mỗi khi họ thắng thêm. Sau khi mở khóa, số trận thắng vẫn tiếp tục được thống kê.
- Người chưa đạt có thể thấy tiến độ: `3/5 trận thắng hợp lệ`, `2/3 đối thủ khác nhau` (ví dụ). Chỉ số lấy từ Server/MySQL, không từ Client.
- Người đã sở hữu thẻ **không bị tước** vì thua các trận giao hữu sau đó; thẻ là thành tựu tích lũy, không phải ngôi vua đương nhiệm.

Các con số **5 thắng / 3 đối thủ** là đề xuất để triển khai đơn giản, chưa phải quyết định bắt buộc của người dùng. Không bắt đầu code kiểm tra ngưỡng nếu chưa duyệt hoặc người dùng chưa yêu cầu dùng mặc định V3.

### Nội dung thẻ hiển thị

```text
┌──────────────────────────────────────┐
│       ÔNG VUA THÁCH ĐẤU              │
│     Thành tựu giao hữu 1vs1          │
│                                      │
│  Thắng giao hữu: 5+ trận hoàn thành  │
│  Đối thủ khác nhau: 3+              │
│  Đạt vào: [ngày được Server ghi nhận]│
│                                      │
│  Không ảnh hưởng BXH chính thức      │
└──────────────────────────────────────┘
```

Có thể dùng icon vương miện/kiếm đôi trên giao diện; phần đồ họa không được thay thế kiểm tra điều kiện trong Server.

### Đồng bộ và chống ghi trùng

- Khi Game giao hữu hoàn tất, lưu kết quả giao hữu bền vững; đánh giá tiến độ/thẻ từ **chỉ những trận WIN hợp lệ**.
- Nếu một Game kết thúc bị finalize hai lần, record giao hữu và thẻ không được cộng trùng; có unique cho mỗi Game/User và unique `User + ACHIEVEMENT_KING_OF_CHALLENGES`.
- Thẻ có `earnedAt` và bằng chứng các kết quả dùng để đạt ngưỡng (truy xuất được) để bảo vệ/demo.
- Reconnect và replay event không phát lại như một phần thưởng mới; Client chỉ đọc trạng thái đã commit.

## 9.9. Công bằng và phạm vi

Không phải thiết kế hệ thống rank giao hữu, Elo hay chức năng chống spam/thông đồng phức tạp. Việc giữ nguyên điểm tích lũy V2 đồng nghĩa ba màn không cân bằng hoàn toàn theo độ lớn điểm, nhưng là **quy tắc người dùng yêu cầu** và chấp nhận được khi đây là giao hữu giải trí. Nếu sau này muốn thi đấu giải, có thể thiết kế luật khác thành một đề án riêng — tuyệt đối không thêm ở V3.

# 10. TƯƠNG TÁC CLIENT–SERVER VÀ TÍNH NHẤT QUÁN DỮ LIỆU

## 10.1. Ranh giới trách nhiệm

| Tác vụ | Server | Client |
|---|---|---|
| Chọn câu, lưu đáp án đúng, deadline | Quyết định và quản lý | Nhận bản công khai và hiển thị |
| Answer, Spin/Star, chấm điểm | Xác thực và xử lý | Gửi hành động, nhận ACK/event |
| Trả thưởng cúp/danh hiệu | Tính từ dữ liệu đã commit, ghi một lần | Hiển thị màn vinh danh |
| BXH toàn Server | Tổng hợp từ MySQL | Hiển thị, tìm kiếm/phân trang |
| Chơi tiếp | Quản lý Room WAITING và START Game mới | Điều hướng và yêu cầu theo quyền |
| Thách đấu giao hữu | Kiểm tra mời/chấp nhận, chọn mode 3, khởi động và ghi kết quả | Gửi lời mời/lựa chọn/đáp án |
| Reconnect | Khôi phục snapshot chính thức, không reset deadline | Kết nối lại và hiển thị trạng thái |

**WebSocket vẫn là kết nối full-duplex** dùng cho các command/event gameplay. Việc dùng hàng đợi outbound riêng và virtual-thread writer theo socket **không chuyển logic gameplay sang Client**. REST phù hợp với trang hồ sơ và truy vấn BXH theo nhu cầu; không cần đưa tất cả thống kê lịch sử lên WebSocket liên tục.

## 10.2. Nguyên tắc cộng thưởng đúng một lần

Điểm kỹ thuật quan trọng nhất của nâng cấp này:

**Một GameSession có thể phát sự kiện kết thúc nhiều lần do retry/reconnect, nhưng thành tích phải được ghi đúng một lần. **`game-result commit` (chốt điểm/kết quả) và `award-decision commit` (chốt eligible/cúp) là hai sự kiện nghiệp vụ khác nhau nếu source thực hiện hai giao dịch.****

Ví dụ lỗi cần chống:

1. A đứng nhất Game 101.
2. Server commit Game FINISHED và trao +1 cúp.
3. WebSocket Client A mất ACK; A reconnect.
4. Event/result bị gửi lại.
5. **Không** được vì gửi lại kết quả mà cộng cúp lần hai.

**Hướng thiết kế:**

- Gắn khóa duy nhất cho bản ghi thưởng theo `gameId + playerId + awardType` (và `mode` nếu là danh hiệu theo mode).
- Chỉ thưởng khi Game đạt trạng thái kết thúc hợp lệ và điều kiện cúp. Lưu **quyết định award bất biến** (kể cả `NOT_ELIGIBLE` và lý do) trong cùng transaction ghi thưởng, chống lần retry đánh giá lại theo trạng thái ban đã đổi.
- Ưu tiên lưu Game result + reward/achievement trong **transaction nhất quán** khi khả thi và an toàn về thời lượng.
- Nếu lifecycle đang chia transaction, cần cơ chế **award finalization chạy lại an toàn** với trạng thái `PENDING → DECIDED` (hoặc tương đương) và startup reconciliation để xử lý khi Server dừng giữa bước chốt trận và bước cấp cúp. **Lần đầu chốt thưởng phải khóa các user liên quan theo thứ tự nhất quán với ban; từ sau commit không được tính lại eligible theo ban/unban mới.**
- Chỉ phát “Bạn đã nhận cúp” sau khi dữ liệu thưởng commit thành công.
- Không để logic phần thưởng chạy trong `SocketOutbound`, callback UI, hoặc thao tác “người chơi mở màn kết quả”.

**Không bắt buộc bổ sung hệ thống queue/worker mới** cho thưởng; với 3–4 Player/LAN, giao dịch DB đơn giản và khóa unique là đủ nếu được gắn đúng vào luồng hiện hành.

## 10.3. Xử lý thất bại

| Tình huống | Hành vi yêu cầu |
|---|---|
| Trận FINISHED nhưng lưu thưởng lỗi transient | Retry an toàn hoặc đánh dấu cần hoàn tất; không phát cúp giả |
| Transaction ghi cúp rollback | Không xuất hiện cúp mới trên hồ sơ/BXH |
| WebSocket chậm/đứt | Không chặn game worker; kết quả vẫn lưu; reconnect/REST đọc lại |
| Hai request đọc BXH cùng lúc chốt trận | Đọc dữ liệu committed; có thể thấy bản trước hoặc sau **award-decision commit**, không thấy một nửa transaction |
| Host đóng Room ngay khi vừa kết thúc trận | Kết quả và thưởng không bị xóa theo Room |
| Server restart sau kết thúc | Có thể dựng hồ sơ/BXH từ DB, không cộng lại cúp |
| Server restart giữa trận | Dùng chính sách `SERVER_INTERRUPTED` hiện tại; không giả định resume trận |

## 10.4. Phân quyền và riêng tư

- Chủ tài khoản được xem hồ sơ của mình; bảng công khai chỉ hiển thị thông tin thống kê cho phép.
- Không cho Client gửi `awardType`, `cupDelta`, `rank` tùy ý để Server tin là chính thức.
- Không cho Client sửa thành tích tài khoản khác bằng cách thay `userId` trong request.
- Không tiết lộ đáp án đúng/hint chưa phát trước khi câu đóng.
- Quyền Host chỉ quản lý Room/Game tương ứng; **không phải quyền admin toàn Server**.
- **Cập nhật V3.3:** ban/kick là **phân hệ ADMIN** ở mục 18; quyền Host **không** cho phép ban toàn Server. Khi bị ADMIN loại, điểm/History vẫn giữ, nhưng award eligibility xử lý riêng (mục 18.6); chỉ triển khai quy tắc này nếu đã duyệt.

---

# 11. MÔ HÌNH DỮ LIỆU Ở MỨC KHÁI NIỆM

**Không phải chỉ dẫn tạo bảng/migration ngay.** Codex phải kiểm tra schema hiện tại và tận dụng tối đa entity/index có sẵn.

```text
Account/User
   ├── PlayerAchievementStats (tổng hợp hoặc VIEW/query)
   │      ├ matches_eligible, wins, cups
   │      └ correct_count, eligible_question_count, ...
   ├── AchievementAward [0..n]
   │      ├ game_id, player_id, award_type, mode?, measured_value
   │      └ earned_at
   ├── FriendlyRecord [0..n]
   │      ├ friendly_game_id, opponent_id, outcome (WIN/DRAW/LOSS)
   │      ├ end_reason (COMPLETED/FORFEIT), counts_for_king_badge
   │      └ finished_at
   ├── FriendlyInvitation [0..n] (hoặc cấu trúc được tái sử dụng)
   │      └ sender, recipient, state, expires_at, request_id
   ├── FriendlyReservation [0..1 đang hoạt động]
   │      └ session_id, participant_ids, state, expires_at
   ├── AccountRestriction [0..n theo thời gian / 0..1 hạn chế hiệu lực]
   │      └ scope, status, expires_at, reason, admin_id, created_at
   └── AdminAuditEvent [0..n]
          └ action, admin_id, target_id, game_id?, time, reason

Room
   └── GameSession [0..n]
          ├── schema version V1/V2 và game_type: NORMAL / FRIENDLY_1V1
          ├── stage plan & question snapshot
          ├── PlayerSession → Answer / score / outcome / timing
          ├── result / status / endReason / final rank
          └── GameParticipantRestriction [0..n] (kick theo gameId + userId)
```

### Nguyên tắc lưu trữ

1. **Lịch sử trận đã có** là nguồn sự thật; không tạo thêm bảng lưu lại từng Answer/score y hệt một lần nữa.
2. Số cúp và win rate có thể **tính từ bản ghi thưởng/kết quả** hoặc dùng bảng tổng hợp để đọc nhanh. Nếu lưu tổng hợp, phải cập nhật nhất quán với bản ghi gốc; tránh dữ liệu hai nơi sai lệch.
3. Với mỗi phần thưởng, lưu được **nguồn gốc** (GameSession nào, người nào, lý do đo được) để kiểm toán và debug.
4. Khóa unique/idempotency ở DB là hàng rào cuối chống cộng cúp trùng khi nhiều thread/transaction cùng thử finalize.
5. Nên có `gameType` hoặc phân loại tương đương để truy vấn chính xác `NORMAL` và `FRIENDLY_1V1`; không suy đoán qua số lượng Player đơn thuần.
6. Quy tắc cúp/điều kiện hợp lệ và danh hiệu cần có **rule version** và giữ bằng chứng đã đạt khi trao; thay đổi ngưỡng sau này không tự tính lại làm mất cúp cũ.
7. Chỉ `NORMAL + V2 + FINISHED + COMPLETED` có thể ghi thưởng BXH chính thức; FRIENDLY có bản ghi thống kê/badge riêng; V1 giữ nguyên History.
8. Reservation giao hữu phải chống tranh chấp với occupancy/START của trận thường; TTL + cleanup phải trả lại chỗ sau timeout/crash.
9. Không cần xây event sourcing/Redis/hệ thống queue mới nếu mô hình hiện tại và ràng buộc DB đáp ứng nhất quán.
10. **Cập nhật V3.3:** `AccountRestriction` và `GameParticipantRestriction` là **mô hình khái niệm**, không ép tạo đúng bảng/tên entity. Cần marker riêng **theo Game** (không dùng ELIMINATED V2), **banVersion + idempotency requestId** khi xử lý account, guard REST/WS/snapshot/outbound, và giải phóng có điều kiện `user_active_game`; xem mục 18. Codex chọn migration nhỏ nhất theo schema có thật.
11. Ghi nhật ký ADMIN không đồng nghĩa xóa tài khoản hoặc sửa lịch sử trận; action gắn với ADMIN/target, bảo vệ transaction/audit và chống replay sau UNBAN. **Host bị ban khi Room WAITING** có đường **ADMIN đóng Room bỏ lại** với audit (mục 18.5.2).

---

# 12. TÌNH HUỐNG BIÊN VÀ QUY TẮC GIẢI QUYẾT

| # | Tình huống | Cách xử lý đề xuất |
|---:|---|---|
| 1 | Trận thường có 2 Player | Theo rule hiện tại, không được Start; không “lách” sang 1vs1 |
| 2 | Trận thường 5 câu | Vẫn chơi và có History, nhưng không cộng cúp/BXH chính thức |
| 3 | Trận 10 câu, ba PLAYER, tất cả đều NO_ANSWER | Hiển thị kết quả, không cấp cúp |
| 4 | Hai PLAYER đồng hạng nhất, cùng điểm và thời gian đúng | Đồng vô địch; nếu trận đủ điều kiện, mỗi người một cúp |
| 5 | Host là SPECTATOR | Không nằm trong thứ hạng/danh hiệu PLAYER, vẫn có quyền Room hợp lệ |
| 6 | Player offline nhưng còn trong roster | Xử lý Answer/NO_ANSWER theo AFK v2 hiện tại, không tự xóa thống kê hay reset deadline |
| 7 | Game bị Host hủy | History đúng loại kết thúc, không cúp/danh hiệu chính thức |
| 8 | Server gián đoạn giữa Game | `SERVER_INTERRUPTED`, không công nhận cúp |
| 9 | Trận FINISHED bị replay event | Không cộng cúp/danh hiệu trùng |
| 10 | Hai Game cùng FINISHED cho hai phòng khác nhau | Kết quả và cúp của mỗi Game tách biệt, không khóa nhầm sang Game khác |
| 11 | Host đổi ngân hàng câu hỏi sau trận | Trận mới dùng snapshot mới; History và thưởng trận cũ không đổi |
| 12 | Người vào Room sau Trận 1 | Không được tính là người dự Trận 1; chỉ tính những trận có PlayerSession hợp lệ |
| 13 | Người rời Room trước Trận 2 | Không tính Trận 2 cho họ; cúp Trận 1 vẫn giữ |
| 14 | Host đóng Room sau khi Game đã kết thúc và Room trở về WAITING | Kết thúc Room, không logout, không xóa thành tích |
| 15 | A và B cùng chọn một mode giao hữu | Yêu cầu người chọn sau đổi mode |
| 16 | Mode thứ ba không có ngân hàng đủ câu | Server chọn trong mode khác đủ điều kiện; nếu không còn mode thì không Start |
| 17 | B từ chối lời mời giao hữu | Hết lời mời, không ghi thắng/thua |
| 18 | A xin thua trong giao hữu | B thắng giao hữu, không cộng cúp chính thức |
| 19 | Hai người giao hữu có cùng điểm cuối trận | RankingEngine V2 xét tổng thời gian trả lời đúng; nếu vẫn bằng thì DRAW, không so số màn thắng |
| 20 | Đổi thuật toán trao badge ở phiên bản sau | Bản ghi thưởng đã cấp giữ nguyên nguồn gốc/version; không tự tính lại lịch sử nếu không có chính sách migration riêng |
| 21 | Game NORMAL V2 status FINISHED nhưng `endReason = CANCELLED` | Không cấp cúp/danh hiệu và không tăng số trận đủ điều kiện |
| 22 | Lịch sử Game V1 tồn tại trước khi nâng cấp | Giữ và hiển thị History, không backfill cúp bằng luật V2 |
| 23 | Hai lời mời được ACCEPT đồng thời cho cùng B | Chỉ một phiên giữ chỗ atomic cho B; lời mời còn lại không thể Start |
| 24 | B nhận lời mời, A cố START trận thường trong lúc chuẩn bị giao hữu | Từ chối thao tác mâu thuẫn theo policy occupancy, không tạo hai Game |
| 25 | Câu Quiz giao hữu có 5 câu | Số Spin = 0 theo công thức gốc; không thêm Spin giả |
| 26 | A thắng hai màn nhưng B có tổng điểm V2 cuối trận cao hơn | B WIN giao hữu; không xét số màn thắng |
| 27 | A thắng 5 giao hữu do B bấm Xin thua | Không đủ tiến độ Ông vua thách đấu vì thắng FORFEIT không tính badge |
| 28 | A đạt 5 thắng COMPLETED trước 3 đối thủ, sau đó thắng tiếp | Cấp thẻ một lần, các trận mới chỉ tăng thống kê giao hữu |
| 29 | ADMIN Kick B đang OPEN câu N mà B chưa Answer | Giữ tập chờ câu N và chấm `NO_ANSWER` theo V2; từ N+1 không tính B nữa; không Early Close giả (18.4.3) |
| 30 | ADMIN Kick B trong Game X và B muốn sang Y | Giải phóng `user_active_game = X` **có điều kiện**, detach Room an toàn; RECONNECT X vẫn bị chặn (18.4.2) |
| 31 | A dẫn đầu nhưng bị Kick trước `awardDecisionCommit` | Không xóa score/rank/History; không cấp cúp/badge cho A; xét award trong nhóm đủ điều kiện theo 18.6 |
| 32 | TEMP_BAN Host khi Game đang ACTIVE | Giữ Game chạy, Host không quản trị; khi WAITING ADMIN có thể đóng Room bỏ lại (18.5.2) |
| 33 | UNBAN xong trong lúc Game cũ ACTIVE | Tài khoản login được nhưng không được reconnect Game đã bị loại bởi ban (18.5.1) |
| 34 | Replay BAN request cũ sau khi ADMIN UNBAN | Trả bản ghi idempotency/hiện trạng; tuyệt đối không ban lại (18.7) |
| 35 | Player bị Kick cố nhận realtime gameplay từ subscription cũ | REST/WS fan-out và outbound chặn, không chỉ chặn command (18.4.4) |
| 36 | Một Room còn 1 Player hợp lệ sau ADMIN kick hàng loạt | Có History nhưng không có cúp/chỉ số trận chính thức theo ngưỡng V3.3 đề xuất (18.6) |
| 37 | Player AFK không còn bị chờ ở `QuestionCloseGate` nhưng vẫn `PLAYING` | **Vẫn có outcome/`NO_ANSWER`** theo scoring set của câu đã OPEN; Early Close không đổi luật AFK |
| 38 | Ban commit trong DECISION trước OPEN; ejection chậm | OPEN đọc **Game-targeted intent đã commit**, không đưa Player vào scoring set mới, không tạo `NO_ANSWER` |
| 39 | Ban rồi gỡ; RoomMember tái sử dụng row khi rejoin | Cleanup cũ CAS theo membership generation; không loại lượt JOIN mới |
| 40 | Ban rồi gỡ; chấp nhận lời mời giao hữu mới | Cleanup cũ chỉ release reservation ID cũ; reservation mới giữ nguyên |

---

# 13. VÍ DỤ DIỄN BIẾN HOÀN CHỈNH

## 13.1. Phòng thường, nhiều chế độ và vinh danh

Phòng `LAN-01` có A, B, C; Host H là spectator. Host chọn ba màn: Quiz (5 câu), Đố mẹo (5 câu), Vua Tiếng Việt (5 câu). Tổng 15 câu nên **đủ điều kiện về độ dài** cho cúp chính thức.

- Các câu Quiz sử dụng **đúng luật Quiz hiện hành**: Spin/Star/điểm phạt/effect nếu được cấu hình và cấp theo số câu Quiz. Với 5 câu Quiz, số Spin có thể bằng 0 theo công thức hiện hành; hệ thống không cấp thêm vì có cúp.
- Đố mẹo và Vua Tiếng Việt dùng luật điểm riêng, gồm thưởng câu cuối màn theo tài liệu hiện tại.
- Server chốt bảng cuối: A hạng 1, B hạng 2, C hạng 3 (số điểm do engine tính, không giả định cố định).
- A được +1 cúp chính thức và +1 trận thắng trên hồ sơ.
- B nếu đúng 100% cả 15 câu có thể nhận **Bách phát bách trúng**, kể cả B không thắng do điểm Quiz có effect hoặc thời gian/tình huống hợp lệ; điều này minh họa danh hiệu **không phụ thuộc duy nhất vào tổng điểm**.
- Chỉ trao Tay nhanh – Đố mẹo nếu có người đạt ít nhất 5 câu đúng thuộc mode đó và đủ điều kiện tốc độ.
- Server lưu thành tích rồi phát vinh danh; H không nhận cúp vì là Spectator.

## 13.2. Chơi tiếp trong Room cũ

- Game 101 FINISHED, Room `LAN-01` về WAITING; A/B/C vẫn là thành viên.
- Bấm `Quay lại phòng / Chơi tiếp`.
- Host đổi cấu hình sang Bài hát → Đuổi hình bắt chữ, tổng 12 câu; kiểm tra lại tác giả ngân hàng và điều kiện START.
- Game 102 khởi tạo điểm từ 0 và snapshot riêng.
- C nếu thắng Game 102 có thêm một cúp, không lấy lại cúp của A ở Game 101.
- Host đóng Room sau Game 102; A/B/C về sảnh, có thể xem cả hai kết quả trong History và BXH toàn Server.

## 13.3. Thách đấu giao hữu — bảng điểm CHUNG, luật cũ không đổi

- A thách đấu B; B đồng ý; Server **giữ chỗ cho cả A và B**.
- A chọn Vua Tiếng Việt, B chọn Đoán tên bài hát; Server chọn Đố mẹo là mode thứ ba từ nguồn câu hợp lệ. Cả hai bấm Sẵn sàng.
- Mỗi màn có 5 câu (mặc định đề xuất). Trận khởi đầu A=0/B=0. **Điểm tiếp tục cộng dồn, không reset ở ranh giới màn.** Luật +10/+20 theo từng câu/mode của V2 được engine chấm như trận thường.
- Sau ba màn, giả sử bảng cuối do Server chốt là **A 100 điểm, B 110 điểm**: **B thắng giao hữu** kể cả A có thể đã đạt điểm cao hơn riêng ở hai màn. Không xét tỷ số màn 2–1.
- Nếu cùng tổng điểm, Server xét tổng thời gian đúng như ranking V2; nếu hai chỉ số đều bằng → hòa.
- Hồ sơ giao hữu tăng B một WIN và A một LOSS; **không cộng cúp hoặc trận thắng NORMAL**. Chỉ những WIN giao hữu hoàn tất `COMPLETED` mới tính tiến độ thẻ **Ông vua thách đấu** theo ngưỡng V3 đề xuất.

**Ví dụ Quiz giao hữu:** Nếu một trong ba màn là Quiz 5 câu, giữ nguyên luật Spin/Star/điểm phạt/streak: có thể không có Spin theo `floor(totalQuizQuestions/10)`. Các hiệu ứng và sàn điểm vẫn do engine V2 hiện tại xử lý, không tạo bảng điểm độc lập cho màn Quiz.

# 14. KIỂM THỬ, THỰC NGHIỆM VÀ BẢO VỆ MÔN LẬP TRÌNH MẠNG

## 14.1. Bộ kiểm thử tối thiểu cho cúp và danh hiệu

1. Trận **NORMAL V2 `FINISHED + COMPLETED`**, 3 Player, ≥10 câu, có câu đúng → phát cúp chính xác.
2. Trận ngắn 1–9 câu → có History nhưng không cấp cúp.
3. 10 câu tất cả `NO_ANSWER` → không cấp cúp.
4. Trận status `FINISHED` nhưng `endReason = CANCELLED`/`SERVER_INTERRUPTED` (hoặc các giá trị tương đương) → không cộng cúp/thắng chính thức.
5. Đồng hạng nhất đúng tie-break → xử lý đồng vô địch đúng quy tắc.
6. Replay result/reconnect/command trùng → cúp và danh hiệu không cộng lặp.
7. Hai transaction cùng finalize Game → DB unique/idempotency bảo vệ.
8. Lỗi MySQL/rollback tại bước cấp thưởng → không tạo dữ liệu thành tích nửa vời.
9. Tốc độ, tỷ lệ đúng của danh hiệu lấy từ dữ liệu Server, không từ điểm Quiz hay Client clock.
10. Tay nhanh so **trong một mode**, đủ 5 câu đúng và ≥60%.
11. Bách phát bách trúng: đủ 10 câu và 100%, NO_ANSWER làm mất điều kiện.
12. Reconnect/AFK trước và sau đóng câu → không thêm quyền Answer, không reset deadline và không làm sai thống kê.
13. Phân quyền: Client không gửi request sửa cúp/achievement cho chính mình hoặc người khác.
14. BXH đồng thời nhiều trận FINISHED → đọc được dữ liệu đã commit nhất quán.
15. Game V1 lịch sử vẫn truy cập được và không tự nhận cúp khi nâng cấp.
16. Hai Player cùng đạt Chuyên gia đa tài (≥3 mode đạt chuẩn) → **cả hai nhận**; một người không đạt bị loại; không phụ thuộc hạng cuối.
17. Đổi cấu hình rule version về sau không tự điều chỉnh cúp đã ghi nhận.

## 14.2. Bộ kiểm thử chơi tiếp

1. Game 1 FINISHED → Room WAITING và roster còn nguyên.
2. Host sửa cấu hình hợp lệ → START Game 2, `gameId` khác, điểm/Answer mới bắt đầu sạch.
3. History và cúp Game 1 giữ nguyên khi Game 2 bắt đầu/kết thúc.
4. Kiểm tra tác giả bộ câu sau khi đổi bộ giữa hai trận.
5. Member Leave giữa hai trận, không bị tính đã chơi Game 2.
6. Host đóng Room sau khi đã chơi nhiều trận, hồ sơ toàn Server vẫn còn.
7. Hai Client cùng bấm Start/retry → chỉ tạo **một** GameSession mới.

## 14.3. Bộ kiểm thử giao hữu — giai đoạn sau

1. PENDING invitation: gửi, từ chối, hết hạn, retry `requestId`; không tạo lời mời trùng.
2. Cạnh tranh hai `ACCEPT` trên cùng User → **chỉ một reserve**; cả hai người của cặp được reserve atomic.
3. ACCEPT rồi A/B START một Game NORMAL khác → bị chặn theo occupancy/policy; không thể hai trận chồng nhau.
4. Reservation expired/cancelled/server restart → giải phóng chỗ, không ghi thắng/thua giả.
5. A/B chọn mode trùng → chặn; Server chọn mode thứ ba hợp lệ và không trùng.
6. Bộ câu A/B làm tác giả hoặc thiếu câu → từ chối có lý do; không lộ đáp án đúng.
7. **Đúng 2 Player**: FRIENDLY Start hợp lệ; NORMAL với 2 Player vẫn không được Start.
8. **Điểm cộng dồn qua 3 màn**: sau mỗi màn vẫn giữ score hiện tại, không reset điểm, streak/Quiz resources trái luật.
9. **Ranking cuối trận theo V2, không theo số màn thắng**: tạo fixture A thắng hai màn nhưng B thắng tổng điểm để chứng minh B WIN; tie điểm xét thời gian đúng; tie cả hai DRAW.
10. Quiz 5 câu có 0 Spin; Quiz 10 câu có lượt Spin theo luật gốc; Star, penalties, floor=0, no-answer, last-question rewards giữ nguyên.
11. Disconnect/reconnect, deadline, duplicate Answer, slow socket, old timer, cleanup giữ giống trận V2 thường.
12. FORFEIT (nếu được duyệt): ghi riêng WIN/LOSS giao hữu, không công nhận badge, không cộng cúp; cạnh tranh với COMPLETED chỉ chốt một kết quả.
13. Trận giao hữu `COMPLETED` ghi đúng WIN/DRAW/LOSS một lần; không thay đổi cúp/trận chính thức/tỷ lệ thắng hoặc BXH chính.
14. **Ông vua thách đấu:** 5 WIN `COMPLETED` ≥3 đối thủ → mở thẻ một lần; 4 WIN hoặc chỉ 2 đối thủ → chưa đạt; FORFEIT không tính; retry event không cấp trùng.
15. Logout/login/reconnect → thẻ và thống kê giao hữu vẫn được lưu đúng tài khoản, không lẫn Room/đối thủ khác.

## 14.4. Demo LAN ba máy — đề xuất

- **Máy Server:** Spring Boot + MySQL, có thể mở trình duyệt Host spectator.
- **Hai hoặc ba máy Client:** Tài khoản Player, cùng phòng qua địa chỉ LAN của Server.
- Chơi Game 1; cho một người mất Wi-Fi rồi reconnect; kết thúc và hiện danh hiệu.
- Mở BXH toàn Server để thấy cúp/hồ sơ được lưu. Đăng xuất/đăng nhập lại vẫn còn.
- Quay lại **cùng Room**, Host cấu hình Game 2 và Start; người chơi không phải nhập lại mã Room.
- Chạy kiểm thử retry kết thúc để chứng minh không cộng cúp hai lần.
- Nếu đã triển khai giao hữu, mời A đấu B và cho thấy BXH chính thức không đổi.

Để đáp ứng yêu cầu môn học, nhóm nên trình bày **sơ đồ kiến trúc, message flow, quy tắc concurrency, transaction, xử lý lỗi, test case và số liệu đo thực**. Không mô tả những kết quả test của Codex là benchmark LAN nếu chúng chỉ chạy trong môi trường kiểm thử.

## 14.5. Điểm đóng góp kỹ thuật (Novelty) đáng báo cáo

Không nói “có cúp và huy hiệu” là innovation kỹ thuật độc lập. Nên nhấn mạnh:

- **Hệ thống thành tích tập trung, Server-Authoritative:** Server tính và lưu cúp/danh hiệu dựa trên outcome đã chốt.
- **Idempotent award finalization:** Không trao thưởng trùng dù event/result bị retry.
- **Đồng bộ đa Client:** Người chơi nhận kết quả chính thức theo WebSocket; hồ sơ/BXH tồn tại trong MySQL.
- **Tái sử dụng vòng đời Room WAITING:** Nhiều GameSession trên một Room nhưng mỗi Game có snapshot độc lập.
- **Khả năng chịu Client chậm đã cải tiến:** FIFO outbound/virtual-thread writer tách khỏi gameplay worker, có số liệu kiểm thử trước/sau theo báo cáo Codex (không phải benchmark throughput).

---

# 15. PHẠM VI TRIỂN KHAI THEO GIAI ĐOẠN

**Khuyến nghị không giao một prompt khổng lồ sửa cả project.** Từng giai đoạn phải có test regression và xác nhận cơ chế đang tồn tại trước khi code.

| Giai đoạn | Mục tiêu | Nội dung ưu tiên | Điều kiện hoàn tất |
|---|---|---|---|
| **0 — Khảo sát/Chốt thiết kế** | Xác minh source hiện tại | Game FINISHED→Room WAITING, History fields, ScoreEngine, Room permissions; xác nhận điều kiện cúp và nguyên tắc giao hữu **tổng điểm V2** | Tài liệu source evidence + quyết định cuối |
| **1 — Vinh danh cơ bản** | Người chơi thấy phần thưởng ngay | Cúp chính thức, Tay nhanh, Bách phát bách trúng, màn kết quả | Test điều kiện, không thưởng trùng |
| **2 — Hồ sơ/BXH toàn Server** | Thành tích lâu dài | Stats theo tài khoản, API đọc, trang hồ sơ, bảng cúp | Logout/login, Room closed không mất dữ liệu |
| **3 — Chơi tiếp & phiên phòng** | Liên tục nhiều trận | Hoàn thiện “Quay lại phòng/Chơi tiếp”, regression 2–3 Game, tổng kết Room nếu đủ thời gian | Không phải tạo Room mới, không lẫn điểm |
| **4 — Danh hiệu nâng cao** | Tăng chiều sâu | Chuyên gia đa tài, Lội ngược dòng, title theo mode có điều kiện | Tính từ dữ liệu tin cậy, không lỗi màn ít câu |
| **5 — Giao hữu 1vs1** | Hai người thách đấu vui vẻ | Invite/accept/reserve, chọn mode, Server chọn mode thứ ba, 2-Player start, điểm & Ranking **chung theo V2**, kết quả riêng, FORFEIT nếu duyệt | Không ảnh hưởng cúp/BXH chính thức; điểm không reset giữa màn |
| **6 — Thẻ Ông vua thách đấu** | Vinh danh giao hữu lâu dài | Thẻ đạt ngưỡng WIN `COMPLETED` trước đủ số đối thủ; lưu một lần; hiển thị tiến độ | Không tác động gameplay/cúp/BXH chính thức |
| **7 — Quản trị Server (đặc tả V3.3)** | Chứng minh Server kiểm soát quyền Client | Login chung/role ADMIN, `/admin`, Kick Game + giải phóng occupancy, lọc REST/WS/snapshot, Ban/unban mọi phiên, Host orphan Room, version/audit/replay, reward eligibility | Game/timer/ranking không đổi; đúng logic khi bị xử lý; test cạnh tranh và LAN |

**Ưu tiên nếu sát hạn nộp:** Hoàn thành giai đoạn **0–3** trước. Giai đoạn 4–5 là mở rộng; đặc biệt 1vs1 không nên làm vội nếu làm mất ổn định gameplay thường.

---

# 16. BẢNG PHÂN BIỆT ĐIỀU ĐÃ CHỐT VÀ THÔNG SỐ KHUYẾN NGHỊ

| Nội dung | Tình trạng | Ghi chú không được suy diễn |
|---|---|---|
| Game V2 tối đa 7 chế độ, Server quyết định gameplay | **ĐÃ CHỐT/ĐÃ CÓ THEO CODEX** | Không đổi engine |
| Quiz có Spin, Hope Star, điểm phạt, streak, sàn điểm V2 = 0 | **ĐÃ CHỐT THEO TÀI LIỆU MỚI NHẤT** | Giao hữu không thay đổi |
| Ngoài Quiz có thưởng câu cuối +20 theo cấu hình/rules snapshot | **ĐÃ CHỐT THEO TÀI LIỆU** | Không loại bỏ trong giao hữu |
| Giao hữu có 3 mode: mỗi bên chọn 1, Server chọn mode thứ ba | **ĐÃ CHỐT Ý TƯỞNG** | Ba mode khác nhau |
| **Giao hữu giữ nguyên luật gameplay và một bảng điểm chung V2** | **ĐÃ CHỐT MỚI NHẤT** | **Không thắng theo màn, không reset điểm/tài nguyên giữa màn** |
| Giao hữu chơi vui, không cộng/trừ cúp/BXH chính thức | **ĐÃ CHỐT** | Thống kê giao hữu riêng |
| Thẻ vinh danh “Ông vua thách đấu” | **ĐÃ CHỐT Ý TƯỞNG** | Ngưỡng mở thẻ là khuyến nghị |
| Cúp, danh hiệu, hồ sơ và BXH toàn Server | **ĐÃ CHỐT HƯỚNG PHÁT TRIỂN** | Điều kiện chi tiết khuyến nghị |
| Chơi tiếp trong cùng Room | **ĐÃ ĐỒNG Ý; CÓ NỀN THEO CODEX** | Tập trung UX/regression, không viết lại Room lifecycle |
| Nâng cấp thưởng chỉ áp dụng Game V2, không tự tính v1 | **QUYẾT ĐỊNH THIẾT KẾ V3 TỪ REVIEW** | Bảo vệ History V1, cần Codex xác minh field version |
| Chỉ NORMAL V2 `FINISHED + COMPLETED` tính cúp | **KHUYẾN NGHỊ V3 TỪ REVIEW** | Tránh `FINISHED` của CANCELLED/INTERRUPTED |
| Cúp chính thức: ≥3 PLAYER, ≥10 câu, ≥1 CORRECT trong eligible group | **KHUYẾN NGHỊ V3** | Không cấm chơi trận ngắn |
| Đồng hạng 1 cùng cúp | **KHUYẾN NGHỊ V3** | Giữ quy tắc ranking tie V2 |
| Tay nhanh theo mode, ≥5 đúng và accuracy ≥60% | **KHUYẾN NGHỊ V3** | Có thể đồng nhận trong cùng mode |
| Bách phát bách trúng: đúng 100%, ≥10 câu | **KHUYẾN NGHỊ V3** | Phải tính cả `NO_ANSWER` |
| Chuyên gia đa tài: đạt mốc ≥3 mode, mọi người đủ chuẩn đều nhận | **KHUYẾN NGHỊ V3 TỪ REVIEW** | Không trao riêng người đứng nhất |
| Lời mời giữ chỗ atomic khi ACCEPT | **YÊU CẦU AN TOÀN THIẾT KẾ V3** | Tránh double booking; triển khai cần đối chiếu occupancy |
| TTL mời 60s, chuẩn bị 180s | **KHUYẾN NGHỊ V3** | Có thể cấu hình |
| Mỗi mode giao hữu 5 câu | **KHUYẾN NGHỊ V3** | Quiz 5 câu → 0 Spin theo luật gốc |
| FORFEIT sau Start | **KHUYẾN NGHỊ V3** | Xử lý kết quả riêng, không bắt buộc MVP |
| Ông vua thách đấu ≥5 WIN COMPLETED/≥3 đối thủ khác nhau | **KHUYẾN NGHỊ V3** | FORFEIT không tính tiến độ, mở thẻ một lần |
| Quản trị ADMIN: kick khỏi trận, cấm tạm thời, ban đến khi gỡ và gỡ cấm | **ĐÃ CHỐT Ý TƯỞNG** | Tính năng mới; chưa được Codex xác nhận tồn tại trong source |
| **Đăng nhập chung**, ADMIN chuyển `/admin` theo role Server | **ĐÃ CHỐT HƯỚNG** | Không tạo `/admin/login`; role chưa có trong source đã review |
| Kick giải phóng occupancy X ngay, detach Room an toàn; giữ tập chờ câu N nhưng loại từ N+1 | **KHUYẾN NGHỊ V3.3, CODEX REVIEW** | Cần chứng minh transaction/lock, Early Close và lịch sử |
| Kick/Ban không nhận cúp Game bị xử lý, lịch sử điểm/hạng vẫn giữ; ngưỡng ≥2 Player hợp lệ | **KHUYẾN NGHỊ V3.3, CẦN DUYỆT** | Award eligibility tách ScoreEngine/RankingEngine, không cấp trùng |
| Ban Host: Game ACTIVE tiếp tục, ADMIN đóng Room WAITING bỏ lại | **KHUYẾN NGHỊ V3.3, CODEX REVIEW** | Không chuyển Host/auto-cancel Game |
| Ban/unban: account-version, replay protection, revoke all sessions, unban không hồi sinh Game cũ | **KHUYẾN NGHỊ V3.3, CODEX REVIEW** | Phải kiểm tra auth registry và WS outbound |

# 17. HƯỚNG DẪN CHO CODEX: KIỂM TRA SOURCE VÀ TRIỂN KHAI AN TOÀN

## 17.1. Cách sử dụng đặc tả V3

1. **Đọc source, migration và test hiện tại trước**. Các đường dẫn Codex từng nêu (`GameTransactions`, `GameHistoryService`, `GameRuntime`, `MultimodeRulesSnapshot`, `RankingEngine`, `game.js`) chỉ là điểm bắt đầu khảo sát. Không tin số dòng cũ hoặc tự suy đoán enum/schema.
2. Lấy tài liệu `GIOI_THIEU_PROJECT_CHO_NHOM(1).md` và rules snapshot **trong source mới nhất** làm chuẩn luật từng mode; nếu hai bên lệch nhau, báo cáo khác biệt và **không tự thay luật**.
3. Coi các ô `KHUYẾN NGHỊ V3` ở mục 16 là **thông số đề xuất**, trình bày rõ trước khi triển khai phần tương ứng; ưu tiên các quy tắc đã được user xác nhận.
4. **Giữ nguyên trận thường**. Không sửa ScoreEngine/RankingEngine Quiz V2 hoặc V1 chỉ để tiện tính cúp. Phân loại gameType/version/endReason tại điểm finalize thích hợp.
5. **Giao hữu:** bắt buộc một bảng điểm V2 tích lũy qua ba màn, không code `best-of-three`; khi cần kết quả cuối thì **lấy ranking chính thức** thay vì tự trừ điểm cuối–đầu màn.
6. **Room→WAITING** đã có theo review: chạy test liên tiếp 2–3 Game trước khi sửa lifecycle; chỉ chỉnh UI hoặc logic khi chứng minh có lỗi.
7. **Thưởng:** idempotent, commit trước khi push, có khả năng retry/reconciliation sau lỗi DB/restart; không cộng thưởng khi Client xem màn kết quả.
8. **Reservation giao hữu:** xác nhận/tạo giữ chỗ cho **cả hai** dưới transaction/lock nhất quán với quyền START game thường; có timeout và dọn chỗ khi hủy/lỗi.
9. **Ông vua thách đấu:** chỉ dựa trên lịch sử `FRIENDLY_1V1` hoàn thành và thắng hợp lệ; không ảnh hưởng bảng chính thức, một thẻ/tài khoản.
10. Giữ isolation mỗi Game, deadline, AFK V2, reconnect, ACK/event FIFO, 4001 logout, socket replace và chính sách tác giả không làm PLAYER bộ câu của mình.
11. **ADMIN (V3.4):** Codex đọc lại role `AuthPrincipal`, `UserAccount`, `AuthService`, toàn session registry, WS outbound registry, `user_active_game`, Game queue, Early Close, Membership, finalize và migrations. Kiểm tra **4 blocker tại mục 18.14 và ba acceptance criteria AC-40–42** trước khi đề nghị sửa code; ưu tiên login chung, kick theo Game, ban theo user, `banVersion`/requestId, outbox/reconcile khi cần.

## 17.2. Đề xuất chia task cho Codex (KHÔNG phải lệnh tự chạy tất cả)

- **Task A — Khảo sát code và chốt acceptance:** Kiểm tra version, endReason, lifecycle, dữ liệu outcome/timing, transaction finalize và logic trang kết quả; lập danh sách phương án tối thiểu.
- **Task B — Cúp + danh hiệu cơ bản:** Bộ lọc NORMAL V2 COMPLETED, cấp cúp đúng một lần, Tay nhanh/Bách phát, test boundary/rollback.
- **Task C — Hồ sơ + BXH toàn Server:** API đọc từ dữ liệu đã commit, bảng cúp và tỷ lệ, giao diện sảnh/hồ sơ, không lộ thông tin bí mật.
- **Task D — Chơi tiếp:** Tận dụng Room WAITING, tối ưu nút UX; integration test 2–3 trận liên tiếp, chốt/đóng Room an toàn.
- **Task E — Danh hiệu nâng cao:** Đa tài theo **mốc thành tựu cho mọi người đạt**, Lội ngược dòng nếu dữ liệu đủ tái dựng; không thêm snapshot DB vô cớ.
- **Task F — Giao hữu 1vs1:** Invite→ACCEPT+reserve→chọn ba mode→READY→START, engine V2/chung điểm, WIN/DRAW/LOSS riêng, quyền/reconnect/error.
- **Task G — Ông vua thách đấu:** Thẻ thành tích từ giao hữu `COMPLETED`, ngưỡng và unique, hiển thị tiến độ và profile, không đổi cúp/BXH chính thức.
- **Task H — ADMIN V3.3 (H0 REVIEW trước):** đối chiếu mục 18 với source và trả lời 4 blocker; khi đã duyệt tách H1 (role + login chung + `/admin`), H2 (Kick + occupancy + queue + outbound), H3 (Ban/Unban + revoke-all + orphan Room + replay), H4 (award integration + security/regression/LAN). **Không chạy H1–H4 trong bước REVIEW.**

**Mỗi task:** yêu cầu Codex công bố phạm vi file sửa, lý do, phương án, chạy unit + integration regression thực có thể chạy, báo cáo PASS/FAIL/SKIP và test chưa chạy, không tự `commit`/`push`, không chạy task kế tiếp khi chưa được yêu cầu.

## 17.3. Acceptance criteria cho toàn hệ thống

- Một Game NORMAL V2 COMPLETED đủ chuẩn chỉ cấp thưởng chính thức **đúng một lần** cho đúng Player; V1, FRIENDLY, CANCELLED và SERVER_INTERRUPTED không cấp.
- Hai Player đồng hạng nhất được đối xử đúng theo rule đã duyệt, không phá hòa bằng ID.
- Danh hiệu phụ không lấy tổng điểm có effect để suy ra số câu đúng; dùng outcome, thời gian và quyền trả lời Server đã lưu.
- BXH/hồ sơ chính thức vẫn đúng sau đóng Room, logout/login và retry kết thúc.
- Trận 2 trong cùng Room tạo GameSession khác, điểm/tài nguyên/Answer mới, lịch sử và cúp trận 1 giữ nguyên.
- Giao hữu 1vs1 mở bằng hai Player mà không hạ điều kiện 3 Player của NORMAL; không được dùng ngân hàng của đối thủ làm tác giả.
- Giao hữu ba màn dùng **luật nguyên gốc và điểm tích lũy**; người thắng là người đứng trên trong final ranking V2, không phải người có nhiều màn thắng.
- ACCEPT giữ chỗ đồng thời hai tài khoản, không double-book khi có hai lời mời cạnh tranh hoặc đồng thời START trận thường.
- Giao hữu không thay cúp/BXH chính thức; thẻ **Ông vua thách đấu** chỉ trao từ thành tích giao hữu đủ điều kiện, không trao trùng.
- Demo LAN nếu có phải đo và ghi đúng điều kiện thử nghiệm; không đổi tên test localhost thành benchmark LAN.
- Phân hệ ADMIN nếu được triển khai: chung trang login nhưng chỉ Server cấp quyền `/admin`; Kick đúng GameSession và giải phóng occupancy an toàn; không phát gameplay qua REST/WS outbound cho tài khoản bị Kick/Ban; ban mọi session, gỡ ban không hồi sinh Game cũ; audit/version/requestId chống replay; kiểm tra Host orphan Room và award eligibility theo **đề xuất V3.2 đã được duyệt**.

## 17.4. Giới hạn phạm vi

Tài liệu này **không yêu cầu** dựng nhiều Server, phục hồi Game đang ACTIVE sau Spring Boot crash, hệ thống mùa giải, MMR, vật phẩm lợi thế hoặc chống thông đồng chuyên nghiệp. **V3.3 hoàn thiện phương án quản trị ở mục 18 theo hai đợt review Codex**, nhưng nó là nhánh triển khai **độc lập** với cúp/danh hiệu/giao hữu; không được tự động thực hiện khi Codex mới được giao review.

**Kết luận nền tảng V3/V3.3:** *Phòng thường có cúp và vinh danh; tài khoản có thành tích dài hạn; Room chơi tiếp được nhờ nền sẵn có; giao hữu là cuộc thi hai người theo đúng luật nhiều màn V2, không ảnh hưởng hạng chính thức; “Ông vua thách đấu” là thẻ thành tựu riêng*. Thiết kế mới tập trung vào tính đúng đắn của nghiệp vụ và khả năng tái sử dụng code hiện có, không mở rộng kiến trúc không cần thiết.


---

# 18. QUẢN TRỊ SERVER V3.4 — PHÂN QUYỀN, KICK, BAN VÀ XỬ LÝ ĐỒNG THỜI

> **Trạng thái:** Người dùng đã thống nhất **ý tưởng ba hình thức xử lý**, **một trang đăng nhập chung** và **ADMIN vào giao diện `/admin` theo role**. Các quy tắc ảnh hưởng engine/lifecycle dưới đây là **PHƯƠNG ÁN THIẾT KẾ ĐỀ XUẤT V3.3 ĐỂ CODEX REVIEW**, chưa phải chức năng đang có hoặc lệnh sửa code. Khi đối chiếu source, nếu phương án không bảo toàn tính đúng đắn, Codex phải chỉ ra xung đột và giải pháp tối thiểu, không tự thay quy tắc gameplay.

## 18.1. Mục tiêu, phạm vi và những gì source đã có

Mục tiêu môn Lập trình mạng: thể hiện **Server thật sự kiểm soát quyền của Client**. Việc ẩn nút, điều hướng khỏi trang, hoặc đóng một socket **không đủ** nếu REST, WebSocket và snapshot vẫn cho truy cập. ADMIN chỉ là người gửi lệnh qua Client; **Spring Boot xác thực quyền, quyết định và lưu hiệu lực**, rồi từ chối Client dù refresh, mở tab mới hoặc đăng nhập lại.

**Theo review tĩnh Codex do người dùng gửi (chưa chạy test):**

- `AuthPrincipal` hiện trả `ROLE_USER`; `UserAccount` và `AuthService` chưa có role ADMIN/ban tương ứng. **Không được mô tả ADMIN là tính năng đã hoàn thành.**
- `AuthSessionRegistry` đã có cơ chế thu hồi theo **session ID**; ban theo tài khoản cần xử lý **toàn bộ session của user**, chặn phiên mới và các request đã qua xác thực.
- Game có boundary tuần tự hóa command; có `user_active_game` để ngăn chơi đồng thời. Kick phải nhất quán với cả hai.
- `AuthenticatedSocketRegistry` phát dữ liệu dựa trên room/game membership; chỉ chặn ANSWER/RECONNECT vẫn **có thể rò gameplay event**. REST snapshot và runtime đọc DB cũng phải kiểm tra trạng thái xử lý.
- V2 không sử dụng trạng thái `ELIMINATED` thay cho Kick; **phải có marker riêng**. Game đã kết thúc chuyển Room về `WAITING` theo báo cáo Codex; không xây lại lifecycle.

**Phạm vi ADMIN (đã đồng ý ý tưởng):** Kick một Player ra khỏi một Game; cấm tài khoản tạm thời; ban tài khoản cho tới khi gỡ; gỡ cấm; xem nhật ký. ADMIN khác HOST. Không làm app Admin riêng, không thêm hệ thống quyền phân cấp phức tạp.

## 18.2. Đăng nhập chung, tài khoản ADMIN cố định và an ninh

### 18.2.1. Một điểm đăng nhập

```text
Web Client hiện có — TRANG ĐĂNG NHẬP CHUNG
             │ username + password
             ▼
   Spring Boot xác thực và đọc ROLE đáng tin cậy từ Server
       ├── PLAYER → giao diện /lobby (hoặc route hiện có)
       └── ADMIN  → giao diện /admin
```

- **Không tạo `/admin/login`**. Tận dụng trang login và cơ chế session/CSRF hiện có; sau khi xác thực, Server trả thông tin vai trò đáng tin cậy hoặc thực hiện redirect phù hợp. Giao diện không tự quyết định `role=ADMIN` dựa vào input Client.
- Tạo trước **một tài khoản ADMIN được quản lý riêng** phục vụ đồ án; không cho đăng ký PUBLIC tự chọn ROLE. Mật khẩu lưu hash theo chính sách auth hiện hành (ví dụ BCrypt); **không hard-code password hoặc tài khoản admin với mật khẩu yếu trong repository**. Quy trình bootstrap qua cấu hình an toàn/migration có kiểm soát, idempotent, không reset password mỗi khi restart.
- ADMIN không phải Player để tự chơi; không có quyền Host mặc định. ADMIN không tham gia Room/Game chỉ nhờ có quyền quản trị. Quyền đọc và ghi tại mọi `/api/admin/**` được Server xác minh, không dựa vào giấu đường dẫn.
- Route `/admin` là yêu cầu UI; mapping thực tế phải theo cách app đang phục vụ static file/client-side routing. Từ chối API trái quyền bằng HTTP 403/WS lỗi tương đương; chính sách trả HTML shell tùy cấu trúc, **dữ liệu/chức năng quản trị luôn bị bảo vệ**.
- MVP: từ chối ban/kick chính tài khoản ADMIN hoặc ADMIN khác. Không bổ sung self-ban, quản trị phân tầng hay giao diện cấp quyền.

### 18.2.2. Màn hình tối thiểu

| Tab `/admin` | Dữ liệu Server trả | Thao tác |
|---|---|---|
| **Trận đang chơi** | gameId, roomId, status, Player/Host, trạng thái kick | Chọn Player → Kick Game ACTIVE; **không hiện nút Kick Host** trong MVP |
| **Tài khoản** | username, role, trạng thái ban có hiệu lực, `bannedUntil`, `version` | Cấm tạm, Ban đến khi gỡ, Gỡ cấm |
| **Phòng bỏ lại** | Room WAITING, Host đang bị ban/không còn quyền | ADMIN đóng Room WAITING theo điều kiện mục 18.5.2 |
| **Nhật ký** | ai xử lý, đối tượng, loại, thời gian, lý do, kết quả | Tìm/xem, không sửa/xóa audit qua UI |

Thao tác nhạy cảm cần xác nhận; ban tạm thời nhập số phút hợp lệ và hiển thị **giờ hết hạn do Server xác định**. Không hiển thị password/hash/sessionId/token, đáp án chưa công bố hoặc dữ liệu không liên quan.

## 18.3. Ba chế tài và ma trận quyền

| Xử lý | Phạm vi | Đăng nhập còn hợp lệ? | Trận đang chơi | Có hiệu lực qua refresh/reconnect? |
|---|---|---|---|---|
| **KICK_GAME** | Một cặp `gameId + userId` | Có | Bị loại quyền chơi/xem gameplay **trận đó**; Game của người khác vẫn chạy | **Có**, nhờ marker Server |
| **TEMP_BAN** | Toàn tài khoản `userId` tới thời điểm Server | Không trong hạn | Bị loại quyền tham gia các Game đang ACTIVE; thu hồi mọi phiên | **Có**, đến khi hết hạn/gỡ |
| **INDEFINITE_BAN** | Toàn tài khoản tới khi ADMIN gỡ | Không | Như TEMP_BAN | **Có**, cả qua restart |
| **UNBAN** | Gỡ chế tài tài khoản | Đăng nhập mới được nếu không còn ban | **Không** xóa marker loại khỏi Game trước đó | Chỉ áp dụng cho phiên/trận tương lai |

KICK_GAME không tương đương logout/ban tài khoản; TEMP_BAN và INDEFINITE_BAN không phải thao tác xóa tài khoản. Cả ba **không chấm lại Answer đã commit, không đổi ScoreEngine/RankingEngine, không tự cancel Game, không xóa History**.

## 18.4. KICK theo Game — quyết định chi tiết để triển khai an toàn

### 18.4.1. KICK gắn GameSession, không dùng `ELIMINATED`

- Lưu **GameParticipationRestriction**/marker phù hợp schema hiện có: khóa unique `(gameId, userId)`, action `KICKED_BY_ADMIN`, `effectiveAt` và nguồn audit; trạng thái này phải truy xuất được khi reconnect, khi fan-out event, khi REST snapshot và khi đọc từ DB. Tên bảng/class chỉ là gợi ý, **không áp đặt tổ chức code**.
- ADMIN chỉ Kick Player thuộc Game ACTIVE; không Kick spectator, người ngoài trận, Game FINISHED hoặc ADMIN. **MVP chặn Kick Host**, kể cả Host đang có tư cách Player (vì chưa thống nhất chuyển quyền Room); UI vô hiệu nút, API vẫn từ chối rõ mã lỗi. Có thể mở rộng trong task khác sau khi duyệt chính sách Host.
- Thao tác Kick đi vào **Game command queue/serialization boundary** giống Answer/Close/Start hoặc boundary tương đương, xác định thứ tự áp dụng duy nhất. Nếu Answer **đã được Server chấp nhận trước** Kick theo thứ tự authoritative, giữ Answer/điểm; request xử lý sau Kick bị từ chối. Timestamp do Client gửi không được vượt hàng rào xử lý.
- **Không** dùng `DISCONNECTED`, `ELIMINATED`, `FORFEIT`, `CANCEL_GAME`, `ALL_ELIMINATED` để giả Kick. Game vẫn ACTIVE cho các Player khác; người bị Kick không thể dùng Spin/Star/Continue/Answer/READY cho Game đó.

### 18.4.2. `user_active_game`: giải phóng ngay nhưng có điều kiện

**Phương án đề xuất V3.3:** để đúng lời hứa “kick khỏi X nhưng có thể vào Y ngay”, việc Kick **phải giải phóng occupancy của X khi trạng thái Kick được áp dụng thành công**, thay vì chờ X kết thúc.

1. Trong cùng trình tự xử lý authoritative của Game, lưu restriction và **xóa occupancy bằng so khớp điều kiện** `(userId, activeGameId = X)`; không xóa trống occupancy của một Game khác nếu dữ liệu đã thay đổi.
2. Nếu các khóa/transaction hiện tại không cho cập nhật marker và occupancy nguyên tử, thiết kế **trạng thái trung gian bền vững** hoặc retry/reconcile idempotent; chỉ trả **KICK_APPLIED** sau khi đạt trạng thái nhất quán. Không báo thành công rồi để tài khoản bị giữ chỗ vĩnh viễn.
3. Phải giải quyết **Room membership** cũ: Player bị Kick rời tư cách tham gia Room/Game cũ cho các hành động tương lai, **nhưng giữ PlayerSession/Answer/Score/History bất biến** cho Game X. Khi thao tác detach chậm/retry, phải nhắm **membership generation của đúng lượt bị Kick**, không đụng một lần rejoin khác dù RoomMember row được tái sử dụng; chi tiết ở AC-42 (18.5.1). Không được gọi bừa endpoint `leaveRoom` nếu nó có thể hủy Game hoặc kích hoạt side effect gameplay. Nếu source không cho detach khỏi Room an toàn, Codex phải báo rõ blocker và đề xuất thay đổi nhỏ nhất.
4. START Game Y và KICK X phải dùng **cùng nguyên tắc đồng bộ occupancy theo user**, hoặc transaction/row lock với thứ tự khóa nhất quán, tránh hai Game đồng thời. KICK không được xóa reservation giao hữu của user khi record đó không thuộc Game X.
5. `gameId + userId` bị Kick vẫn chặn vĩnh viễn việc vào **X**, dù occupancy đã giải phóng. Chơi Game Y theo rule START/membership của Y; không tự động được đưa vào trận đã bắt đầu.

**Không có đường giải phóng đúng và kiểm chứng được thì không tuyên bố demo “X → Y ngay” đã đạt**. Trong bước review Codex phải đưa sơ đồ lock/transaction cụ thể và test cạnh tranh KICK(X)–START(Y).

### 18.4.3. Ranh giới QUESTION/DECISION/SCORING, Early Close và NO_ANSWER — V3.4

**BẮT BUỘC TÁCH HAI TẬP (AC-40; bổ sung từ review Codex V3.3):**

| Tập / thời điểm chụp | Mục đích | Nguyên tắc với người offline bình thường | Nguyên tắc với Kick/Ban |
|---|---|---|---|
| **`questionParticipationSet(N)`** hoặc `scoringSet(N)` (mô hình logic) — chụp khi `QUESTION_OPEN(N)` thành công | Ai **được tham gia câu N và bắt buộc nhận outcome khi chấm** (`CORRECT`/`WRONG`/`NO_ANSWER` tùy luật gốc) | **Vẫn bao gồm** Player còn trạng thái gameplay đủ điều kiện (ví dụ `PLAYING`) dù AFK/offline và đã bị bỏ khỏi tập chờ đóng sớm; khi chưa Answer thì vẫn có `NO_ANSWER` theo luật V2 | Nếu restriction hoặc game-targeted ban intent **đã có hiệu lực trước commit OPEN(N)**, **loại khỏi set N**. Nếu xử lý sau OPEN(N), giữ nghĩa vụ chấm N và không nhận Answer mới sau fence |
| **`earlyCloseWaitSet(N)`** / `QuestionCloseGate` (mô hình tương ứng source) — được engine lấy/chụp theo luật hiện có | Ai cần nộp hoặc đợi trước khi **có thể đóng câu sớm**; không phải nguồn chân lý cho scoring | Có thể **không bao gồm** offline/AFK sau ngưỡng theo luật hiện có; điều này **không miễn `NO_ANSWER`** | Không xóa người khỏi wait set **đã chụp cho N** chỉ vì Kick/Ban giữa OPEN; set của câu/phase kế tiếp được xác định lại theo quyền mới |
| **`introReadySet` / `decisionParticipants`** (nếu có) | Kiểm soát chuyển pha và quyền hành động, **không thay** tập scoring câu đã OPEN | Tuân theo timeout/reconnect hiện hành | Kick/Ban không tự tạo READY, không tự CLOSE, không phát sinh `NO_ANSWER` khi câu **chưa** OPEN |

`questionParticipationSet` ở đây là **hợp đồng nghiệp vụ phải bảo toàn qua scoring/recovery**, **không khẳng định source đã lưu sẵn một bảng snapshot**. Codex phải đọc `GameLifecycle`, `QuestionCloseGate`, `GameTransactions` để chỉ ra nguồn dữ liệu có thể tái dựng chính xác; nếu nguồn hiện hành chỉ dựa vào `PLAYING` runtime và có nguy cơ mất danh sách sau Kick, phải bổ sung persistence tối thiểu cho snapshot câu hoặc outcome eligibility. **Không dùng `QuestionCloseGate` làm input chấm điểm** và không suy luận rằng "offline được loại khỏi Early Close" đồng nghĩa "offline không bị phạt".

**Quy tắc quan trọng:** `QUESTION_CLOSED` chỉ có nghĩa **ngừng nhận Answer**, **không có nghĩa** Answer đã được chấm. Theo source Codex mô tả: `QUESTION_OPEN → QUESTION_CLOSED → SCORING → RESULT`. Có thể tồn tại Answer `ACCEPTED_UNSCORED` giữa CLOSE và SCORE. **Điểm ranh giới để bảo toàn nghĩa vụ chấm là `questionScoringCommitted` hoặc trạng thái bền vững tương đương**, không phải nhãn phase `CLOSED`.

Chính sách **question-boundary** được đề xuất như sau: **Nếu câu N đã thực sự mở trước khi Kick/Ban có hiệu lực và chưa scoring xong, toàn bộ nghĩa vụ chấm thuộc snapshot câu N phải được hoàn tất đúng một lần**. Không được lọc người chơi đã bị restriction ra khỏi phép chấm N, dù restriction được áp dụng ở pha CLOSE/SCORING. Người bị loại không tham gia **câu chưa mở** sau đó. Đối với Kick, ranh giới là trình tự Game queue; đối với Ban, lấy **commit ban** làm hàng rào quyền nhận Answer mới, còn scoring của câu đã mở vẫn dùng snapshot cố định.

| Phase ở thời điểm xử lý chế tài | Điều kiện câu N | Quy tắc scoring và tập chờ Early Close của N (hai tập độc lập) | Từ câu tiếp theo |
|---|---|---|---|
| `DECISION` Quiz trước `QUESTION_OPEN` | **Câu N chưa mở** | Không tạo `NO_ANSWER`/penalty câu N; dữ liệu Spin/Star/effect đã commit trước chế tài được giữ lịch sử, **không hoàn lại** | Không đưa người bị loại vào OPEN N và các câu sau |
| `QUESTION_OPEN`, **đã có Answer được Server chấp nhận** | N đã mở, chưa scoring | Giữ `ACCEPTED_UNSCORED`; chấm đúng một lần, áp dụng điểm/effect V2 gốc | Loại khỏi roster eligible mới |
| `QUESTION_OPEN`, **chưa nộp** | N đã mở, chưa scoring | Giữ wait set N đã chụp nhưng dùng **scoring set N riêng** để tạo `NO_ANSWER` theo luật gốc khi N được SCORE; Kick không thay đáp án hoặc đóng N sớm | Không tạo `NO_ANSWER` từ N+1 |
| `QUESTION_CLOSED` hoặc đang `SCORING` **nhưng chưa commit score** | N đã mở, tồn tại Answer chưa chấm/thiếu outcome | **Bắt buộc hoàn tất scoring N từ snapshot at OPEN**, không loại người đã Kick/Ban khỏi job SCORE; bảo toàn Answer/NO_ANSWER/effect | Không tạo bản ghi cho câu chưa mở sau này |
| Câu N đã `RESULT` và **scoring commit hoàn tất** | N đã chấm đủ | Không chấm lại, không thay điểm/history N | Loại khỏi câu tiếp theo |
| `INTRO`/chuyển màn | Không có câu đã mở tương ứng | Không sinh score/penalty; giữ nguyên điều kiện và timeout INTRO đang chụp, Kick không làm auto-ready | Không đưa người bị loại vào INTRO/READY mới |

**Các bất biến triển khai:**

1. **Question participation snapshot** là tập **chấm/outcome**, **khác** tập chờ Early Close; được cố định lúc OPEN và là đầu vào chấm câu N (kể cả khi restart/recovery dựa DB). Nếu người chơi bị loại giữa câu, snapshot N vẫn giữ họ; không cho Answer mới sau hiệu lực restriction/ban.
2. **Tách cổng nhận hành động và cổng chấm:** kiểm tra restriction/ban khi nhận Answer/Spin/Star; nhưng tác vụ `SCORING(N)` dùng `participationSnapshot(N)` + các Answer đã commit trước fence, **không** lấy danh sách `currentlyActivePlayers` hoặc account-ban hiện thời để bỏ qua nghĩa vụ của N.
3. Nếu trước OPEN có `DECISION`, effect/Spin/Star đã commit giữ trong History/audit; không refund tài nguyên, không biến việc rời Game thành câu bỏ trả lời. **V3.4 không tạo thêm quy tắc điểm**; nếu source không lưu rõ effect giữa pha thì Codex phải nêu cách tận dụng dữ liệu hiện có.
4. Early Close và INTRO: **không loại người khỏi wait set đã chụp giữa phase**; Kick/Ban không tính là Answer/READY; hết hạn hoặc đủ điều kiện theo luật hiện tại mới chuyển phase. **AFK/offline thông thường vẫn chịu NO_ANSWER khi nằm trong scoring set**, dù vắng mặt ở wait set; không gộp hai tập. **Tập của pha/câu kế tiếp** được tính sau khi loại người bị restriction.
5. `CLOSED ≠ SCORED`; kiểm tra `SCORING_COMMITTED`/unique outcome để tránh tính `NO_ANSWER` hai lần, kể cả khi Game worker retry. Nếu kick sau CLOSE trước SCORE, **đáp án đã accept bắt buộc được chấm**.
6. Trong cùng một GameSession, điểm/hạng lịch sử vẫn được engine cập nhật cho câu N (nếu N đã mở) dù occupancy X đã giải phóng để tham gia Game Y; **không được tái chiếm occupancy X** do job SCORE hay Game finalize muộn.

**Ví dụ bắt buộc phải kiểm chứng:** A nộp Answer câu N (persist `ACCEPTED_UNSCORED`) → Server CLOSE N → ADMIN Kick A → SCORING N → outcome/score N của A **vẫn được ghi đúng một lần**. Ngược lại, nếu Kick ở `DECISION` **trước OPEN**, A không có outcome `NO_ANSWER` cho N.

### 18.4.4. Chặn cả đường nhận lẫn đường phát dữ liệu

| Cổng | Kiểm tra bắt buộc |
|---|---|
| REST request thay đổi gameplay | Kiểm tra account ban + game restriction + membership; từ chối trước mutate |
| WebSocket handshake và authenticated command | Check account ban khi bắt tay và mỗi command; Kick check riêng cho mỗi game command |
| Reconnect/resume Game X | Từ chối theo marker Kick **trước** trả snapshot Game; unban không vượt được restriction cũ |
| REST game snapshot / question / state | Không cấp **dữ liệu gameplay còn ACTIVE của X** cho người bị Kick, kể cả khi dữ liệu được đọc từ DB |
| Subscription, fan-out, outbound socket FIFO | Kiểm tra ban/Kick **ở thời điểm chọn người nhận và sát thời điểm writer dequeues/sends payload**; dừng event gameplay Game X sau khi marker có hiệu lực |
| Dữ liệu History sau Game X đã kết thúc | **Cho xem History chính mình** theo quyền hiện có và chính sách không lộ đáp án chưa công bố; Kick không xóa dữ liệu cũ |

Vì `SocketOutbound` theo review Codex hiện xếp hàng `TextMessage`, **nên đề xuất thêm envelope tối thiểu có `gameId`/dataScope/recipient/sessionGeneration hoặc cơ chế purge+recheck tương đương** để writer bỏ frame gameplay cũ mà không đảo FIFO; không ép thay toàn bộ queue. Event đã bắt đầu thao tác gửi trước commit không thể thu hồi khỏi mạng; không tuyên bố mạng bảo đảm không giao frame đó sau thời gian commit. **Game worker không được chờ network I/O**. Server có thể gửi **một sự kiện thông báo chế tài phi-gameplay** cho target để Client hiển thị lý do, sau đó hủy subscription Game X và/hoặc đóng đúng socket game liên quan. Từ chối an toàn **không phụ thuộc** việc event thông báo có tới đích hay không. Event đã gửi ra mạng trước thời điểm Kick không thể thu hồi; mục tiêu kiểm thử là **không enqueue/phát dữ liệu mới sau điểm hiệu lực**, và rà soát event còn nằm trong hàng đợi outbound để không lọt sau hạn chế.

## 18.5. Ban tài khoản khi đang có Game — thứ tự và quyền quay lại

### 18.5.1. Commit ban, intent gắn đúng phiên/Game và ejection idempotent — V3.3

**Mốc có hiệu lực bảo mật là transaction ban COMMIT.** **AC-41:** Ngay tại giao dịch chụp `questionParticipationSet(N)` khi `OPEN(N)`, Server phải xét `GameParticipantRestriction(X,U)` **hoặc Game-targeted ban intent đã commit** nhắm `(X,U)` dù ejection/membership cleanup chưa chạy. **Ban commit trước OPEN commit ⇒ loại U khỏi câu N; OPEN commit trước ban commit ⇒ giữ U trong nghĩa vụ chấm N**. Không đợi ejection runtime rồi mới loại; không lấy thời gian nhận thông báo hoặc ACK làm mốc. Codex phải chỉ rõ **commit fence dùng chung với Ban và OPEN** (ví dụ các User row được khóa theo thứ tự ổn định trong transaction OPEN, không chờ Game queue khi đang giữ User lock; hoặc cơ chế khác chứng minh tuyến tính hóa), bởi một truy vấn snapshot không khóa có thể đọc trước Ban nhưng COMMIT OPEN sau Ban. Chỉ chấp nhận phương án chống được race này bằng test barrier. Ejection Game và việc đóng socket có thể hoàn tất sau đó, nhưng không được tạo khoảng hở nhận lệnh, reconnect hay gửi dữ liệu gameplay được bảo vệ. Đặc biệt, **tác vụ cũ không được tác động phiên/Game mới sau UNBAN**.

**A. Transaction account-ban — không chờ Game worker khi đang giữ User lock**

1. Sau kiểm tra ADMIN/CSRF/requestId/expectedVersion, transaction khóa đúng `User`/account sanction. Dùng **cùng hàng rào nhận vào Game tại User row** với START/ACCEPT: ban đọc occupancy/reservation từ dữ liệu bền vững **mà không giữ hay chờ Room/Game lock**; START/ACCEPT phải tranh User lock ở commit. Điều này ngăn occupancy mới chen giữa snapshot và ban commit.
2. Trong cùng transaction, ghi trạng thái `banKind`, `banVersion`/`sanctionEpoch` mới và audit; lưu **intent bền vững** liên kết `adminActionId`, `targetUserId`, `banVersion`, `effectiveAt` do Server cấp, **danh sách GameSession mục tiêu tại commit** (nếu đang tham gia), **`membershipGeneration` theo đúng Room membership target, các `reservationId` cần hủy**, và **ranh giới thế hệ phiên xác thực bị thu hồi**. Nếu một mục tiêu không tồn tại tại commit, **không quét tìm lượt mới khi retry**. Nếu Game FINISHED nhưng `awardDecision` vẫn PENDING, lớp award phải đọc ban đã commit trước khi tự quyết định (mục 18.6).
3. COMMIT rồi **thả User lock**. Chỉ sau đó mới xếp ejection vào Game queue, dọn occupancy, đóng socket, thông báo Client hoặc retry intent. **Tuyệt đối không giữ User lock khi đợi Game/Room queue**, tránh deadlock với thứ tự khóa được Codex báo cáo `Room → Game → User`.
4. Bất kể task phụ đã chạy chưa, login/API/WS outbound phải đọc trạng thái ban đã commit; REST/reconnect Game X **và thao tác `QUESTION_OPEN` tạo tập tham gia/chấm** còn kiểm tra **restriction marker *hoặc* game-targeted intent đã commit nhưng chưa materialize**. Nếu source dùng cache trạng thái, phải có cơ chế vô hiệu hóa/fence nhất quán; không được chỉ đọc snapshot stale.

**A.1. Ban COMMIT trước OPEN nhưng ejection đến muộn — điều kiện triển khai AC-41**

- Tình huống: Game X đang `DECISION(N)`; `BAN(U)` **commit**, sinh `intent(actionId, gameId=X, userId=U)`; tạm dừng Game ejection; Game queue chạy `QUESTION_OPEN(N)`; sau đó mới chạy ejection.
- Tại transaction OPEN(N), Server nhận thấy **intent đã commit** và **không** đưa U vào `questionParticipationSet(N)`. U không có Answer/outcome `NO_ANSWER(N)` vì **N chưa mở với U**; những người còn lại không bị mất lượt, không đổi timer/luật Quiz. Nếu Spin/Star của U đã được commit trước ban trong `DECISION`, chỉ giữ lịch sử tiêu tài nguyên; không hoàn trả.
- Nếu OPEN(N) đã commit trước BAN thì U **có mặt trong set chấm N**; BAN ngăn Answer mới từ thời điểm commit và ejection trễ **không xóa** nghĩa vụ chấm N. Hai thứ tự commit tạo hai kết quả khác nhau **có chủ đích**.
- Bảo đảm lock/fence: `OPEN` có Game/Room lock theo thiết kế hiện tại, khi cần đọc/khóa User thì tuân thứ tự **Room → Game → User theo thứ tự userId tăng**; BAN chỉ giữ User lock trong DB transaction riêng, thả hết trước khi enqueue Game ejection. Nếu Codex thấy thứ tự thực tế khác, phải trình bày phương án tránh deadlock và chứng minh bằng test, không áp dụng máy móc tên lock ví dụ.
- Tương tự, nếu account TEMP_BAN tự hết hạn thì **Game X đã bị ban-targeted intent commit trong lúc ACTIVE vẫn bị loại khỏi X** sau expiry/UNBAN; không chỉ đọc trạng thái ban hiện thời và vô tình cho chơi lại X.

**B. Định danh thế hệ phiên và Game — chống thu hồi nhầm**

- Mỗi phiên auth có **định danh/đời phiên bất biến** (session ID duy nhất hoặc generation) để phân biệt trước/sau UNBAN. Intent chỉ thu hồi **những phiên đã tồn tại ở thời điểm ban** hoặc có `issueGeneration ≤ banCutoffGeneration` theo cơ chế Server cấp; không chạy `revokeAllSessionsByUser(userId)` một cách mù quáng khi replay tác vụ cũ.
- Có thể chụp chính xác session IDs của thời điểm ban **nếu registry đảm bảo đầy đủ mọi phiên**, hoặc dùng `authEpoch`/`revocationCutoff` bền vững kiểm tra ở mọi request/WS. **Không dùng timestamp Client hoặc đồng hồ máy để suy thế hệ session**; session ID không được ghi lộ trong audit/log.
- Intent ejection gắn `(targetGameId, targetUserId, banActionId/banVersion)`; khi retry **chỉ** tạo marker/cleanup đối với `targetGameId`, chỉ CAS xóa occupancy nếu nó **vẫn thuộc Game đó**, chỉ detach **đúng thế hệ Room membership** và release **đúng reservation ID** được chụp trước đó (AC-42). Game Y sau unban không bị xóa occupancy, không bị kick hoặc mất socket bởi intent ban của Game X.
- **UNBAN hoặc hết hạn không xóa nghĩa vụ ejection Game X** đã được ban commit khi X ACTIVE. Restriction X có thể được materialize muộn nhưng **fence Game X** phải truy vấn được ngay từ intent; không cho người dùng reconnect X ở khoảng giữa UNBAN và ejection. Chỉ các phiên tạo **sau khi quyền account được khôi phục** mới được dùng cho Game mới; session ban cũ không hồi sinh.
- **AC-42 — Membership/rejoin:** Theo review Codex, `RoomMember` có thể **tái sử dụng cùng hàng khi người chơi rejoin**. Do đó ban intent phải gắn **`targetRoomId`, `membershipId` (nếu có), `membershipGeneration`/`joinVersion` bất biến cho lượt đang bị xử lý**, không được chỉ nhắm `(roomId,userId)` hoặc chỉ `roomMemberId`. Mỗi lần JOIN/REJOIN hợp lệ tạo **generation tăng/định danh lượt mới**, kể cả khi tái sử dụng row. Cleanup chỉ thực hiện bằng `UPDATE ... WHERE membershipId=? AND generation=? AND state=?` (CAS tương đương); **0 rows affected** khi thế hệ đã khác là kết quả no-op đúng, không retry bừa thành quyền xóa mới. Nếu Room đã đóng/xóa thì xử lý idempotent theo hiện trạng mà không ảnh hưởng Room khác.
- **AC-42 — Reservation/giữ chỗ:** Target của cleanup phải lưu **`reservationId` duy nhất cho mỗi lần giữ chỗ** (kèm generation/version trạng thái nếu reservation row có thể tái sử dụng). Ban cũ chỉ được hủy/release đúng `reservationId` đã chụp, không chạy lệnh `delete reservations where userId=...` trong retry. Cặp ACCEPT mới sau UNBAN có reservation mới và vẫn ACTIVE/RESERVED. Tương tự với pending invitation, thao tác hủy chỉ nhắm những ID thuộc ban action snapshot. Dọn occupancy vẫn CAS theo `(userId, gameId=X)`.
- **Snapshot và synchronization:** Khâu tạo thế hệ membership/reservation, `ACCEPT/START`, và ban phải dùng **hàng rào đồng bộ trên User** hoặc điều kiện tương đương để chụp được target tại ban commit. Không lấy Room/Game lock trong ban transaction khi đang giữ User lock; capture target id/generation theo cơ chế chống tranh chấp và thả User lock rồi mới chạy cleanup. Không dựa vào timestamp/row ID để đoán lượt mới.
- Nếu một account đã ban, chuyển TEMP→INDEFINITE hoặc gia hạn TEMP, mỗi action có version và intent riêng. Retry phải idempotent theo `(actionId,effectType,targetGameId|sessionGeneration)`; không quét Game/Room hiện tại bằng trạng thái mới tại lúc tác vụ cũ chạy.

**C. Câu hỏi đang mở và Answer đang tranh chấp**

- Commit ban trước Answer commit → **Answer phải bị từ chối/rollback**. Answer commit trước ban → **giữ Answer**, dù ACK đến Client sau khi ban; câu đã OPEN vẫn được chấm ở `SCORING`, theo mục 18.4.3.
- Không đủ an toàn nếu chỉ kiểm tra ban lúc vào controller: giữa kiểm tra và Answer commit có thể bị ban. Codex phải dùng **hàng rào commit** chung (ví dụ User lock theo thứ tự được review, hoặc version validation + transaction retry phù hợp) để bảo đảm quy tắc trên.
- Có thể ACK một Answer đã commit trước ban **sau** ban do độ trễ mạng. ACK không quyết định kết quả nghiệp vụ và không chứng minh lệnh sau ban được chấp nhận.

**D. Khôi phục/reconcile và phạm vi**

- Reconciler đọc pending intents từ DB theo target/action/version, áp dụng qua Game boundary đúng thứ tự, ghi trạng thái từng effect; lỗi mạng không khiến ban mất hiệu lực. Nếu action cũ chạy sau UNBAN, **chỉ** hoàn tất các mục tiêu X/phiên cũ đã chụp; không tác động Y/phiên mới.
- Không tự cancel Game ACTIVE, không đổi công thức score/ranking, không tạo `NO_ANSWER` cho câu chưa OPEN, không tự đóng Room ACTIVE. Khi người bị ban là Host, áp dụng 18.5.2.

**Bài test bắt buộc:** `BAN(X, version 5)` commit → pause ejection/revocation → `UNBAN(version 6)` → user có session mới và vào Game Y → chạy lại toàn bộ pending effects version 5. Kỳ vọng: session/Game Y **không bị đụng tới**, Game X vẫn chặn reconnect, session trước ban vẫn vô hiệu.

### 18.5.2. Host bị ban và Room bị bỏ lại

- **Host đang tham gia Game ACTIVE bị TEMP/INDEFINITE_BAN:** cấm tài khoản có hiệu lực ngay; nếu Host có tư cách Player thì áp dụng Game restriction như mục 18.5.1. **Game của các Player khác vẫn chạy theo timer/queue Server**, không tự chuyển Host và không auto-cancel Game. Host mất quyền Room/API vì bị ban.
- **Room còn ACTIVE:** ADMIN **không** đóng Room giữa trận chỉ để xử lý Host; đợi engine kết thúc, hoặc dùng luồng kết thúc đã được chốt trong project. Không phát sinh endReason giả.
- **Khi Room về `WAITING` mà Host vẫn bị ban/không còn quyền:** thêm quyền hạn hẹp **`ADMIN_CLOSE_ORPHAN_WAITING_ROOM`**, chỉ cho ADMIN và chỉ khi Room WAITING + trạng thái Host không thể quản lý được Server xác minh. Xác nhận → đóng Room, giải phóng membership/occupancy phù hợp, thông báo Player còn trong phòng và đưa về lobby; **không logout tài khoản khác**, không xóa History/Game, có audit/idempotency.
- **Room WAITING tại thời điểm ban Host:** ADMIN có thể đóng theo cùng điều kiện; không bắt buộc auto-close nếu ADMIN chưa thao tác. Không tự chỉ định Host mới trong MVP.
- **Người bị ban đang ở Room WAITING nhưng không phải Host:** loại quyền tham gia/cấu hình từ tài khoản đã ban, giải phóng membership và các reservation/lời mời giao hữu chưa START của user bằng transaction/cleanup an toàn; **không được hủy nhầm Room của Host khác** hoặc giữ chỗ khiến Player còn lại không START được. Khi ban hết hạn, user cần tham gia Room/lời mời mới theo rule thường, không khôi phục reservation đã hủy.
- **Ban khi đang chuẩn bị giao hữu:** nếu một bên trong ACCEPTED/RESERVED/SELECTING/READY bị ban, kết thúc phiên chuẩn bị với lý do bị quản trị chặn, giải phóng **cả hai chỗ giữ**, không ghi WIN/LOSS, không sinh thẻ “Ông vua thách đấu”; không đợi timeout đầy đủ rồi mới trả chỗ. Nếu đã START thì áp dụng ejection của Game ACTIVE và không công nhận kết quả giao hữu bị can thiệp (mục 18.6).
- **Host chỉ offline nhưng không bị ban:** duy trì chính sách Room hiện có; chức năng ADMIN đóng phòng bỏ lại **không phải nút đóng mọi Room của người khác**.

## 18.6. Chính sách thành tích khi Kick/Ban — eligible group và award-decision COMMIT

Đây là **chính sách khuyến nghị V3.3**, tách chấm điểm và thứ hạng gốc khỏi **quyền nhận thưởng chính thức**. Không thay ScoreEngine/RankingEngine. Quyết định cấp thưởng **không căn cứ chỉ vào Game `FINISHED`**, mà căn cứ `awardDecision` được chốt bền vững bằng transaction riêng nếu source hiện có chia hai bước.

| Dữ liệu/quyền | Player eligible | Player bị KICK hoặc game-targeted BAN restriction |
|---|---|---|
| Answer/score/rank History câu đã OPEN trước chế tài | Theo rules V2 gốc | **Giữ và hoàn tất chấm N**, kể cả Answer `ACCEPTED_UNSCORED` trước Kick/Ban và `NO_ANSWER` của N nếu rules yêu cầu |
| Từ câu chưa OPEN kế tiếp | Được quyền tham gia | **Không** thêm `NO_ANSWER`, Answer, Spin, Star hoặc READY mới |
| Cúp/danh hiệu chính thức Game X | Được xét nếu Game đủ chuẩn | **Không nhận**, kể cả có điểm cao nhất lịch sử |
| `officialGamesCount` | +1 khi Game được công nhận và người chơi eligible | Không tăng cho Game X |
| `officialWinsCount` | +1 nếu được trao danh hiệu thắng chính thức | Không tăng cho Game X |
| Danh hiệu Tay nhanh, Bách phát bách trúng, Đa tài, Lội ngược dòng | Tính từ dữ liệu và điều kiện **trong tập eligible** | Không được trao từ Game X |
| Trận giao hữu bị ADMIN can thiệp | Thống kê giao hữu theo chính sách riêng | **Không công nhận WIN `COMPLETED`** để tích “Ông vua thách đấu”; History lưu rõ việc can thiệp |
| Thành tích Game đã award commit trước đó | Giữ | **Không thu hồi hồi tố** vì Kick/Ban sau này |

**Bộ điều kiện công nhận Game NORMAL V2** (mọi điều kiện đều phải đúng **tại transaction quyết định award**):

1. `schemaVersion = 2`, `gameType = NORMAL`, `status = FINISHED`, `endReason = COMPLETED`, dữ liệu Game/Answer/rank đã commit đầy đủ.
2. Snapshot ban đầu có **≥3 Player** không phải Spectator; Game thực sự có **≥10 câu** theo tiêu chuẩn mục 5.2 (đều là ngưỡng đề xuất, chưa được người dùng phê duyệt).
3. Tạo **tập `eligibleForOfficialAwards`** tại thời điểm chốt thưởng từ roster Game, restrictions `(gameId,userId)` đã commit, **game-targeted pending intents** chưa materialize và account ban **đang có hiệu lực tại thời điểm chốt**. Không suy ra eligible từ room membership đã rời; không tính người bị loại chỉ vì disconnected bình thường.
4. Tập eligible có **≥2 Player** sau can thiệp ADMIN (nếu không có can thiệp thì mặc nhiên dùng roster bình thường, đồng thời vẫn áp điều kiện chuẩn). Nhóm eligible có **ít nhất một `CORRECT` đã chấm** thuộc một câu họ được quyền chơi. **`CORRECT` của người bị Kick/Ban không đáp ứng điều kiện này**.
5. Nếu tất cả đạt, chọn **rank cao nhất trong tập eligible** bằng comparator/tie policy V2 **trên các kết quả gốc đã lưu**, không sửa `finalRank`/score. Người đồng rank ở mức thắng đều nhận +1 cúp; danh hiệu cạnh tranh (ví dụ Tay nhanh theo mode) **xét và tìm người đứng đầu lại trong tập eligible**, không chọn hạng cao nhất toàn roster rồi chỉ bỏ họ mà không xét người kế tiếp.
6. Nếu không đạt bất kỳ điều kiện nào, lưu `awardDecision = NOT_QUALIFIED` cùng reason (`INSUFFICIENT_ELIGIBLE_PLAYERS`, `NO_ELIGIBLE_CORRECT`, ...); **không tăng cả tử và mẫu thống kê chính thức cho bất kỳ ai**. Giao diện vẫn hiển thị History/result theo engine.
7. **Một khi quyết định award đã commit thì nó bất biến:** không hồi tố vì Admin ban/unban sau đó, không tự chạy lại eligibility khi retry/restart. Chỉ trả lại quyết định đã lưu hoặc hoàn tất tác dụng phụ cùng transaction/idempotency, không trao thưởng trùng.

**Ví dụ phản biện Codex:** A đúng 1 câu duy nhất rồi bị Kick; B,C hoàn toàn không có `CORRECT`. Dù trận `FINISHED + COMPLETED`, có ≥10 câu và còn hai eligible B,C, **không ai được nhận cúp**, bởi điều kiện `NO_ELIGIBLE_CORRECT`. Trái lại, nếu B đúng ít nhất một câu còn B,C cùng đủ chuẩn, B/C được xét theo ranking gốc; A không được nhận cúp/danh hiệu dù vẫn đứng đầu History.

### 18.6.1. Hai mốc commit phải được phân biệt

- **`gameResultCommit`**: lưu kết quả/điểm/ranking cuối, đặt Game `FINISHED`. Có thể xảy ra trước khi có cúp.
- **`awardDecisionCommit`**: trong transaction độc lập hoặc cùng `gameResultCommit` nếu khả thi, **đóng băng** eligible participants, reasons, danh hiệu, cúp, `officialGamesCount`/`officialWinsCount`; trạng thái `DECIDED` bền vững và unique key chống trùng. Đây là **mốc duy nhất quyết định quyền thưởng**. UI chỉ thông báo “đã nhận cúp” sau commit này.
- **Ban/Kick commit trước `awardDecisionCommit`** phải được xem xét theo nguồn quyền tương ứng. Kick theo Game queue có marker hoặc durable intent; Ban commit trên User row được tính bằng lock/fence. **Nếu ban có hiệu lực trước award** thì target không được nhận thưởng; **nếu ban chỉ commit sau award** thì không hồi tố. Khi UNBAN trước award mà Game X đã có removal intent/restriction thì X vẫn không eligible; trường hợp account ban sau khi Game đã FINISHED nhưng chưa award, eligibility đánh giá theo trạng thái ban hiệu lực lúc award chốt, trừ khi có restriction Game bền vững.
- **Thứ tự commit, không phải thời điểm gửi/nhận ACK hoặc thời điểm Client nhấn nút**, là cơ sở quyết định. Với request bị tranh chấp, Server phải tạo thứ tự commit rõ ràng và có test pause ở transaction boundary.

### 18.6.2. Transaction chốt thưởng — phương án ít thay đổi engine nhất

1. `awardDecision` có khóa duy nhất `gameId` (và bản ghi thưởng unique theo `(gameId,userId,awardType,mode?)`), trạng thái `PENDING/DECIDED/NOT_QUALIFIED` hoặc tương đương. Đọc `DECIDED`/`NOT_QUALIFIED` → **return** dữ liệu đã lưu; không đánh giá lại.
2. Nếu `PENDING`, khóa Game/award-decision theo thứ tự lock đã xác minh, rồi **khóa User rows liên quan theo `userId` tăng dần**, nhất quán với ban chỉ khóa User rồi thả trước khi vào Game queue. Khóa này buộc **ban commit và award commit có thứ tự**, không xảy ra award đọc `NONE` trong khi ban đồng thời đang chờ commit. Nếu không thể khóa user rows, cần một cơ chế transactional fencing tương đương mà Codex chứng minh bằng test.
3. Trong transaction, truy vấn các marker/intent Game X bền vững và account sanctions theo trạng thái nhất quán; tính eligible snapshot, xác nhận `CORRECT` **trong tập này**, chốt winner và award rows/stat delta **trong cùng transaction**; ghi `DECIDED` ngay cả khi **không có ai nhận cúp**. Không dùng `FINISHED` như cờ đã thưởng.
4. `KICK` xử lý trong Game queue; nếu kick thắng trước final Game event/award thì marker đã commit hoặc game-targeted intent phải được award nhìn thấy. Nếu Game đã terminal theo thứ tự queue thì Kick mới bị từ chối; policy ban vẫn dựa trên thời điểm commit User và `awardDecisionCommit`.
5. Crash trước transaction award commit → mọi thay đổi thưởng rollback, retry xét theo các sanction đã commit thực tế. Crash sau commit nhưng trước WebSocket ACK → retry đọc quyết định đã lưu, **không cộng lại**. Ban sau commit không làm giảm cúp đã cấp.

**Cảnh báo lock:** Không giữ User lock trong lúc **đợi** Game queue hoặc Room queue. Source được Codex báo có nền `Room → Game → User`; việc khóa nhiều User để chốt award phải theo thứ tự cố định. Nếu cần transaction Room/Game/User kết hợp, Codex mô tả order thật bằng call graph và test, **không áp đặt lock order chỉ từ tài liệu**.

**Nhãn trạng thái cho UI:** `RESULT_READY`, `AWARDS_PENDING`, `AWARDS_DECIDED`, `AWARDS_NOT_QUALIFIED`; không nhầm “Game đã kết thúc” thành “đã cấp thưởng”. Đây là các trạng thái logic, không ép tạo enum mới nếu UI/API hiện tại có cách biểu diễn tương đương.

## 18.7. TEMP_BAN / INDEFINITE_BAN / UNBAN — cập nhật nhất quán và không replay lệnh cũ

### 18.7.1. Một trạng thái ban hiệu lực và phiên bản

**Phương án đề xuất:** một projection trạng thái hiệu lực theo user: `banKind = NONE | TEMP | INDEFINITE`, `bannedUntil` (chỉ TEMP), `banVersion` tăng mỗi lần trạng thái thay đổi; audit bất biến lưu lịch sử. Với TEMP, hiệu lực khi `serverNow < bannedUntil`; tại hoặc sau hạn thì user không bị cấm nữa dù chưa có cron cleanup. Không dùng đồng hồ trình duyệt làm nguồn sự thật. Schema thực tế có thể khác, nhưng phải có dữ liệu thể hiện cùng hành vi.

| Trạng thái ban hiện có | Lệnh ADMIN mới | Kết quả đề xuất |
|---|---|---|
| NONE hoặc TEMP đã hết hạn | TEMP (thời lượng hợp lệ) | Bắt đầu TEMP mới từ **giờ Server lúc commit**, expiry tuyệt đối |
| TEMP còn hạn | TEMP với `requestId` **mới** và `expectedVersion` hiện tại | **Thay thế** hạn bằng `serverNow + duration`, không cộng chồng phút |
| TEMP còn hạn | INDEFINITE | Nâng lên ban vô thời hạn |
| INDEFINITE | TEMP | **409 CONFLICT**: không âm thầm hạ mức; phải UNBAN rồi ban tạm bằng lệnh mới |
| TEMP/INDEFINITE | UNBAN | Đưa về NONE, tăng `banVersion`, audit |
| NONE/đã hết hạn | UNBAN | Trả no-op idempotent, không tạo hiệu lực mới; audit/no-op tùy cách ghi nhất quán |
| INDEFINITE | INDEFINITE requestId mới | Có thể trả `ALREADY_BANNED`/no-op, không đổi version nếu không có quyết định nghiệp vụ khác |

Mỗi request thay đổi ban có `requestId` duy nhất, `expectedVersion` được Client lấy từ trạng thái gần nhất. Server lưu khóa idempotency **vĩnh viễn đủ lâu cho lịch sử audit**: retry cùng `requestId` + cùng payload **không lặp hành động**, không gia hạn TEMP, không tạo audit thứ hai; cùng requestId nhưng payload khác → từ chối. **Replay request Ban cũ sau khi đã UNBAN phải chỉ trả kết quả của lần xử lý trước kèm trạng thái hiện hành, tuyệt đối không áp dụng lại chế tài**.

Hai ADMIN cùng thao tác (dù MVP tạo một ADMIN, vẫn cần bảo vệ đồng thời): lock/compare-and-swap theo user và `expectedVersion`. Một lệnh thành công tăng version; lệnh dựa trên version cũ nhận **409 STALE_VERSION** và phải tải lại. Nếu không dùng version cột hiện có, có thể chọn optimistic locking JPA hoặc transaction row lock tương đương. **Không được dùng thời điểm Client gửi để quyết định ai thắng.**

### 18.7.2. Transaction, session revocation và recovery

1. Xác thực role/CSRF; kiểm tra target không là ADMIN; validate duration/reason/requestId/expectedVersion.
2. Transaction **theo user**: khóa target → tra idempotency → kiểm tra version/chuyển trạng thái → **chụp game target, session cutoff/epoch, `membershipGeneration`, `reservationId` của đúng action** → ghi audit + intent bất biến → COMMIT. Lỗi DB → trả lỗi, **không phát thông báo “đã ban”**. Không giữ User lock khi chờ Game queue (18.5.1).
3. Ngay sau commit, Server xem ban là có hiệu lực; kiểm tra ban ở login, request, WS và **lúc gửi dữ liệu**. Thu hồi **mọi phiên/socket thuộc thế hệ trước ranh giới ban** (kể cả nhiều thiết bị); retry của action cũ **không được thu hồi phiên mới sau UNBAN**.
4. Tác dụng phụ chưa hoàn thành (đóng socket phiên cũ, gắn restriction đúng Game mục tiêu, dọn occupancy theo `gameId`, **detach membership bằng generation**, **release reservation bằng ID/version**) phải **retry/reconcile** khi Server restart. Chặn reconnect theo **marker hoặc intent Game-targeted** ngay cả khi account đã UNBAN. Retry không được quét/đụng tới Game mới.
5. UNBAN chỉ mở đường đăng nhập/khởi tạo session mới; **không hồi sinh session cũ**, không xóa dấu Kick hoặc removal khỏi Game ACTIVE, không khôi phục thời gian câu hỏi/Spin/Star đã mất.

## 18.8. REST, WebSocket, snapshot và ranh giới bảo mật

**Mọi route chỉ mang tính minh họa**; Codex map vào pattern sẵn có, không tự tạo API mới nếu một đường an toàn đã đáp ứng:

| API / luồng | Dữ liệu/kiểm tra | Quyền |
|---|---|---|
| `GET /api/admin/games` | ACTIVE games + participant restrictions; phân trang | ADMIN |
| `POST /api/admin/games/{id}/kick` | targetUserId, reason, requestId; Game queue + occupancy | ADMIN |
| `GET /api/admin/users` | role/ban/expiry/version, không lộ secret | ADMIN |
| `POST /api/admin/users/{id}/temporary-ban` | minutes, expectedVersion, requestId | ADMIN |
| `POST /api/admin/users/{id}/ban` | expectedVersion, requestId | ADMIN |
| `POST /api/admin/users/{id}/unban` | expectedVersion, requestId | ADMIN |
| `POST /api/admin/rooms/{id}/close-orphan` | requestId, WAITING + Host bị ban | ADMIN |
| `GET /api/admin/audit` | pagination/filter | ADMIN |
| Game Answer/Spin/Star/INTRO/RECONNECT/snapshot | active account + không bị loại đúng game + membership | Player hợp lệ |

- Client không truyền role đáng tin cậy, kết quả Kick hay trạng thái ban tùy ý. Không dựa vào `Authorization` ở trang HTML riêng lẻ; mọi đường dữ liệu REST/WS cần guard đúng thời điểm.
- `AuthenticatedSocketRegistry` phải lọc ở recipient-selection **và** thời điểm gửi outbound. Kiểm chứng cả **socket cũ, socket thay thế, nhiều tab, re-subscribe, reconnect, DB-backed snapshot**, kể cả event gameplay phát theo Room. Tránh sửa WS FIFO/outbound worker hiện có một cách không cần thiết.
- Trạng thái account ban sau commit có ưu tiên chặn trên phạm vi toàn hệ thống. Với Kick Game X, chỉ cấm gameplay của X, không chặn lịch sử cá nhân/lobby/Game Y. Mã lỗi nên phân biệt `GAME_KICKED`, `ACCOUNT_TEMP_BANNED`, `ACCOUNT_INDEFINITE_BANNED`, `STALE_VERSION`; thông điệp rõ nhưng không tiết lộ dữ liệu bí mật.
- Cần xem lại policy `SESSION_REPLACED`/close code đang có trước khi chọn close reason/code mới; **không tái sử dụng ý nghĩa cũ sai**.

## 18.9. Mô hình dữ liệu đề xuất (không ép tên bảng)

```text
user_account:          userId, role/authority ... [giữ schema hiện tại]
account_sanction:      userId UNIQUE, banKind, bannedUntil?, banVersion, updatedAt
admin_action:          actionId, requestId UNIQUE, adminId, targetUserId,
                       gameId?, roomId?, action, payloadFingerprint,
                       before/after, reason, createdAt, outcome
participant_restriction:
                       (gameId, userId) UNIQUE, reason = KICK/BAN,
                       effectiveAt, appliedAtQuestionId?, adminActionId?
room_membership:       existing RoomMember identity + monotonically changing
                       membershipGeneration (new value per join/rejoin)
friendly_reservation:  reservationId UNIQUE, reservationVersion/state, two users
account_effect_intent: actionId, userId, banVersion, targetGameId?, authCutoffGeneration?,
                       targetRoomId?, membershipId?, membershipGeneration?,
                       reservationId?, reservationVersion?,
                       effectType, state, retryInfo, createdAt
game_award_decision:    gameId UNIQUE, state, eligibleUserIdsSnapshot, reason?,
                       decisionCommittedAt, policyVersion
```

- Đây là **logical model**; `room_membership` và `friendly_reservation` ở đây chỉ nêu **thuộc tính định danh lượt**, không đòi tạo thêm bảng nếu source đang có RoomMember/đối tượng reservation phù hợp. Codex có thể hợp nhất bảng hoặc dùng field hiện hữu nếu bảo toàn unique/transaction/race. Không thêm toàn bộ bảng chỉ vì sơ đồ đề xuất.
- `user_active_game` hiện có là **occupancy**, không phải History và không thể tự xóa nếu nó đã chuyển sang Game khác. Cần CAS hoặc lock dùng chung với START/ACCEPT giao hữu.
- History/PlayerSession/Answer vẫn được lưu dù rời Room sau Kick; không cascade xóa lịch sử từ membership.
- `account_effect_intent` chỉ cần nếu không thể hoàn tất side effect trong một transaction; nếu có thì **mỗi intent là action-scoped và game/session/membership-generation/reservation-id-scoped, không đánh lại target khi retry**, đồng thời có thể tham gia game restriction guard khi marker chưa materialize.
- Audit do Server ghi; tất cả timestamp theo Server, không ghi cookie/mật khẩu/CSRF/session ID thô hay đề thi chưa công bố. `game_award_decision` là mô hình logic: có thể hợp nhất bảng hiện tại; bắt buộc có **trạng thái chốt + key unique**, kể cả trường hợp không đủ chuẩn để tránh retry trao cúp sai.

## 18.10. Ma trận cạnh tranh theo mốc COMMIT — V3.3

| Cạnh tranh | Mốc quyết định và chính sách | Bất biến cần test |
|---|---|---|
| Kick vs Answer trong cùng Game | **Game queue/commit order** quyết định; Answer trước Kick giữ, sau Kick từ chối | `ACCEPTED_UNSCORED` không bị xóa vì Kick đến trước SCORE |
| Kick `QUESTION_CLOSED` trước `SCORING` | Câu đã mở: scoring phải hoàn tất theo snapshot câu đó | Không mất Answer, không chấm lặp |
| Kick trong Quiz `DECISION` trước `QUESTION_OPEN` | Chưa có question participation N | Không `NO_ANSWER` N; Spin/Star đã commit không refund |
| Kick vs Early Close/INTRO | Giữ wait/ready set của phase đang chạy; loại từ phase mới; **scoring set độc lập với wait set** | Không CLOSE sớm, **offline vẫn có NO_ANSWER theo V2** khi thuộc scoring set, không kẹt N+1 |
| Kick X vs START Y | Restriction + CAS xóa occupancy X; START Y tuân thủ lock user | Không xóa nhầm occupancy Y |
| Kick X vs reconnect/snapshot/outbound X | Marker restriction có hiệu lực ở Server trước cấp dữ liệu | Không vượt bằng reconnect/DB snapshot/WS queued frame cũ |
| Ban vs Answer | **Answer commit trước ban commit** giữ; **ban commit trước Answer commit** chặn/rollback | ACK có thể tới muộn; ACK không là mốc nghiệp vụ |
| Ban vs revocation delayed | Intent version V nhắm phiên/generation đúng thời điểm ban | Retry V sau UNBAN không kill phiên mới |
| Ban X vs ejection delayed rồi user vào Y | Intent V nhắm `(userId,gameId=X)`; occupancy CAS | Không kick/clear Y; X vẫn block reconnect |
| Ban vs game scoring N đang CLOSE | Ban chặn Answer mới, không lọc Answer đã commit hoặc `NO_ANSWER` của N | Scoring N hoàn tất trước/sau ejection |
| **Ban commit trước `QUESTION_OPEN(N)` trong khi ejection bị delay** | OPEN transaction kiểm tra pending Game-targeted intent/marker **và dùng commit fence với ban** | U không ở scoring set(N), không sinh `NO_ANSWER(N)`; OPEN trước ban thì giữ outcome N |
| **Ban old cleanup vs rejoin/accept sau unban** | CAS `membershipGeneration`/`reservationId` đã chụp trong intent | Cleanup cũ là no-op với JOIN mới và reservation mới; không xóa quyền Y |
| Ban/Kick vs award | Award đọc game-targeted restriction/intent + ban hiệu lực **trong award-decision transaction có locking/fencing** | `CORRECT` chỉ của eligible; winner chọn trong eligible |
| Ban commit trước award decision commit | Ban được xét khi transaction quyết định thưởng; nếu có Game marker thì giữ loại ngay cả sau UNBAN | Không trao nhầm cúp khi ban trước, không hồi tố nếu ban sau |
| Award decision commit trước ban commit | Decision bền vững thắng; ban không hồi tố | Ban đến sau không sửa cúp/mẫu số cũ |
| Ban vs multiple admins/unban | `requestId + payloadFingerprint`, expectedVersion, User lock | Replay no-op, stale version 409 |
| Ban Host vs Game ACTIVE/Room WAITING | Game tiếp tục; orphan WAITING được ADMIN đóng đúng điều kiện | Không auto-cancel Game; không chuyển Host ngầm |
| Server crash sau ban commit trước effect | Pending intent/version snapshot tồn tại | Login guard ngay và reconcile đúng Game/phiên cũ |
| Server crash giữa Game result và award commit | Decision `PENDING`, retry bền vững | Không phát thưởng giả, không tính lại khi đã `DECIDED` |

**Quy ước thứ tự:** xử lý đồng thời phải có một **điểm tuyến tính hóa có thể kiểm chứng** (transaction COMMIT hoặc Game queue command commit). Không dựa vào thời điểm Admin nhấn nút, HTTP ACK, WS ACK hoặc timestamp Client. Với thao tác trải qua nhiều transaction, phân biệt **bảo mật account** có hiệu lực ở ban commit với **scoring câu đã OPEN**, **ejection Game** và **award-decision commit**.

## 18.11. Quy tắc hiển thị, History và audit

- **Người bị kick:** “Bạn đã bị quản trị viên đưa ra khỏi trận này.” Có thể quay lại sảnh/hồ sơ và tham gia Game mới khi đủ điều kiện. Không trả gameplay snapshot hay câu hỏi ACTIVE của Game vừa kick. Khi trận cũ FINISHED, History của chính họ vẫn có thể xem, chứa điểm và Answer trước đó theo policy công khai đáp án của project.
- **Người bị cấm tạm:** “Tài khoản bị cấm đến [giờ Server và múi giờ hiển thị].” Không đăng nhập, không dùng session cũ trong hạn; hết hạn đăng nhập mới. Không tự reconnect trận đã bị loại do ban.
- **Người bị ban vô thời hạn:** “Tài khoản bị cấm cho đến khi quản trị viên gỡ cấm.” Không hiển thị lý do nội bộ quá mức cần thiết.
- **ADMIN:** mọi thao tác có trạng thái **thành công/không thay đổi/bị từ chối/chờ hoàn tất tác dụng phụ**; Game `FINISHED` có thể hiển thị `AWARDS_PENDING`, chưa tuyên bố người nhận cúp. Không báo thành công trước commit; sau ban có thể hiển thị “đã có hiệu lực, đang hoàn tất ngắt kết nối” nếu có tác vụ gửi event còn đang xử lý.
- **History:** không xóa Answer/Score đã ghi; có thể hiển thị nhãn “rời trận theo quyết định ADMIN” và `eligibleForAwards=false`, nhưng không cố tái tính score theo công thức khác.
- **Audit:** ai, mục tiêu, Game/Room nếu có, thao tác, thời điểm, version và requestId, reason ngắn, kết quả; bất biến. Chỉ ADMIN đọc, không để Player suy ra username/session/token không được phép xem.

## 18.12. Kiểm thử chấp nhận ADMIN — bắt buộc trước khi demo LAN

### A. Auth/Authorization và đăng nhập chung

1. Một trang đăng nhập; ROLE_USER chuyển lobby, ROLE_ADMIN chuyển `/admin`; Player sửa JSON, URL, JS hoặc tự gọi API không thể trở thành ADMIN.
2. Admin được provision an toàn/idempotent, không tạo ADMIN bằng đăng ký thường; password hash, không có secret trong source.
3. ADMIN không tự có Host/Player; không thể cấm ADMIN hoặc tự xử lý chính mình qua API; CSRF/session guard hiện có còn nguyên.

### B. Kick, timer và occupancy

4. Game X ACTIVE có ba Player; ADMIN kick non-Host B; marker `(X,B)` lưu bền vững; B bị từ chối ANSWER/Spin/Star/INTRO/RECONNECT/X-snapshot.
5. Socket cũ/room subscription/DB snapshot không gửi câu hỏi ACTIVE X cho B; event đã gửi trước Kick không bị coi là lỗ hổng sau hiệu lực.
6. Câu N OPEN chưa nộp: giữ tập chờ N, B nhận `NO_ANSWER` đúng luật tại N, không thêm NO_ANSWER từ N+1; không đóng N sớm vì Kick.
7. Câu N đã nộp: giữ Answer và điểm; Kick không làm trùng score, không reset effect, không sửa snapshot History.
8. INTRO đang chờ: không treo hoặc nhảy sớm ngoài rule; từ phase tiếp theo không chờ B; Host/Spectator không đổi.
9. Sau KICK_APPLIED, occupancy X được giải phóng có điều kiện; B rời room membership cũ **theo đường an toàn**, có thể tham gia/START Game Y khi hợp lệ. Race Kick X/Start Y không double-book.
10. Game FINISHED, Host đang chơi hoặc User không phải Player → API Kick từ chối đúng lý do; người khác tiếp tục chơi, không auto-cancel Game.
11. Kick giữa CLOSE và SCORE → Chấm đầy đủ nghĩa vụ câu đã OPEN; Kick trước/đang Game result commit tuân thủ Game queue, eligibility chốt riêng ở award decision transaction.

### C. Ban/Unban, Host và cạnh tranh

12. TEMP_BAN 10 phút tính hạn theo Server; hết hạn login được; giữa hạn mọi phiên/sockets/API bảo vệ đều bị chặn.
13. INDEFINITE_BAN qua restart vẫn có hiệu lực; chỉ UNBAN mở đường đăng nhập mới.
14. Ban có 2+ active sessions/sockets: toàn bộ mất quyền; close chậm không giúp gửi lệnh hoặc nhận gameplay event.
15. Ban Host ACTIVE: Game tiếp tục, Room về WAITING; ADMIN đóng orphan WAITING có audit, không logout Player khác. Không đóng Room ACTIVE.
15a. Ban Player trong Room WAITING hoặc chuẩn bị giao hữu RESERVED: dọn đúng membership/reservation; không treo người chơi đối diện hay hủy Room của Host khác.
16. Unban/hết hạn trước khi Game cũ FINISHED: user đăng nhập được nhưng không reconnect Game cũ, vẫn có thể chơi Game tương lai.
17. TEMP → INDEFINITE hợp lệ; INDEFINITE → TEMP bị 409; TEMP mới thay hạn theo giờ Server; UNBAN thay đổi version.
18. Hai thao tác ban/unban `expectedVersion` cũ → 409; retry cùng `requestId` không audit lần hai; replay BAN cũ sau UNBAN **không ban lại**.
19. Crash giữa ban commit và socket/ejection → Server restart vẫn kiểm tra ban, reconcile Game marker, dọn occupancy đúng, không mất audit.

### D. Thưởng, dữ liệu và regression

20. B hạng 1 nhưng bị Kick trước `awardDecisionCommit`: History giữ điểm/rank B; B không cúp/danh hiệu/trận chính thức; nếu A,C còn đủ điều kiện, xét cúp theo thứ hạng A/C.
21. Chỉ còn một Player không bị admin loại: không có cúp/trận chính thức theo ngưỡng đề xuất; không xóa History.
22. Ban commit sau `awardDecisionCommit` không hồi tố; ban commit trước `awardDecisionCommit` phải được xét trong transaction chốt eligibility, dùng row lock/fence; `gameResultCommit` không đồng nghĩa cấp cúp.
23. Giao hữu bị can thiệp ADMIN không sinh WIN `COMPLETED` để farm “Ông vua thách đấu”; luật Quiz v2/V1 History, các effect, 7 mode không bị thay.
24. Điểm sàn 0, penalty `NO_ANSWER`, Spin/Star/streak, Early Close, Reconnect, WS FIFO, session replacement, xử lý Room WAITING và 2–3 Game liên tiếp vẫn PASS regression.
25. Demo LAN trên ít nhất hai thiết bị/trình duyệt độc lập chứng minh hành vi **Server từ chối** sau khi thao tác ADMIN, không chỉ redirect/ẩn nút.

### E. Regression cạnh tranh bắt buộc bổ sung V3.3

26. `OPEN(N)` → Answer A commit thành `ACCEPTED_UNSCORED` → `QUESTION_CLOSED(N)` → Kick A → `SCORING(N)`: Answer A vẫn có outcome/score, không trùng; câu N+1 không có A.
27. Kick khi `SCORING(N)` chưa commit: retry scorer đúng một lần, không bỏ A khỏi tập scoring N.
28. Kick/ban ở `DECISION(N)` sau commit Spin/Star nhưng **trước OPEN(N)**: không sinh `NO_ANSWER(N)`; effect đã tiêu vẫn ghi History, không hoàn trả; những người còn lại chơi bình thường.
29. A bị Kick lúc `OPEN(N)` chưa nộp: luật V2 quyết định `NO_ANSWER(N)` một lần; không tạo outcome cho N+1. Giữ wait set N, INTRO không vượt deadline.
30. Ban commit sau Answer commit nhưng ACK Answer bị delay: vẫn phải lưu/chấm Answer; ACK có thể đến sau ban. Ban commit trước Answer commit: Answer rollback/từ chối dù request đã đi vào controller.
31. Ban X version V → tạm dừng game ejection và session revocation → UNBAN V+1 → đăng nhập session mới/START Game Y → chạy lại task version V: **Game Y, phiên mới, occupancy Y còn nguyên; Game X vẫn bị chặn**.
32. Pending marker Game X chưa materialize → unban → thử `RECONNECT(X)` bằng session mới: **bị từ chối nhờ intent Game-targeted**, không có cửa sổ hở.
33. A là **người duy nhất có `CORRECT` nhưng đã bị Kick**, B,C eligible không có `CORRECT`: Game có History nhưng `NO_ELIGIBLE_CORRECT`, không ai nhận cúp, không tăng `officialGamesCount`.
34. A bị Kick và đứng đầu History; B,C eligible có ít nhất một `CORRECT`: chọn người cao nhất **trong B/C**; Tay nhanh cũng xét lại **trong eligible** theo mode; không sửa final rank.
35. Game `FINISHED`, `awardDecision=PENDING`: ban commit trước award transaction → A không eligible; test đảo commit order (award commit trước ban) → A đã nhận cúp **không bị thu hồi**. Không dùng ACK để giải thích kết quả.
36. Ban và award commit đua nhau trên cùng User: dùng barrier/deterministic tests chứng minh khóa User hoặc fence tương đương quyết định tuyến tính hóa, không có kết quả half-updated.
37. Crash sau game result commit trước award commit → reconcile đúng; crash sau award commit trước WS emit → không cấp trùng; `NOT_QUALIFIED` đã commit không biến thành eligible sau UNBAN.
38. WS outbound có nhiều frame Game X xếp hàng + frame Game Y; Kick/Ban rồi writer gửi chậm: frame X **chưa bắt đầu gửi** sau fence bị drop có kiểm tra scope, frame Y hợp lệ và FIFO vẫn đúng; không bắt Game worker chờ I/O.
39. Hai thao tác Ban, UNBAN cạnh tranh và hai Admin cùng cập nhật: request cũ `expectedVersion` không thể thắng commit mới; audit và pending-intent idempotent.

### F. Ba acceptance criteria bổ sung sau review V3.3 — bắt buộc (AC-40–AC-42)

40. **AC-40a — AFK offline nhưng vẫn bị chấm:** Câu N mở với A,B,C, B ở trạng thái `PLAYING` nhưng offline đủ lâu để `QuestionCloseGate` **không còn chờ B**. Tới `SCORING(N)`, B **vẫn** thuộc `questionParticipationSet(N)` và nhận outcome `NO_ANSWER(N)`/penalty đúng luật V2 nếu không Answer; không lấy gate làm nguồn để bỏ B. Người còn lại vẫn được Early Close đúng điều kiện hiện hành.
41. **AC-40b — Kick trước OPEN vs giữa OPEN:** D bị KICK trước commit OPEN(N) ⇒ D không thuộc scoring set N và không có outcome N; E bị KICK sau commit OPEN(N) ⇒ E còn nghĩa vụ outcome N nhưng không được gửi Answer mới, E không tham gia scoring set N+1. So sánh với offline bình thường, không nhập nhằng hai đối tượng.
42. **AC-41a — BAN trước OPEN, ejection delay:** `DECISION(N)` → `BAN(U)` COMMIT (intent Game X bền vững) → pause ejection → `QUESTION_OPEN(N)` COMMIT → `SCORING(N)` → mới chạy ejection. **U không ở scoring set N**, không sinh Answer/`NO_ANSWER(N)`; Spin/Star đã commit trước ban vẫn lưu lịch sử; retry và restart không sinh outcome N muộn.
43. **AC-41b — Đảo thứ tự COMMIT:** `QUESTION_OPEN(N)` COMMIT với U còn eligible → `BAN(U)` COMMIT → ejection delay → `SCORING(N)`; U **vẫn** nhận outcome N theo luật, kể cả `NO_ANSWER` nếu không Answer. Chạy nhiều lượt race BAN/OPEN bằng barrier trên điểm lock/commit; không chấp nhận giả định thứ tự dựa vào lúc API đến/ACK.
44. **AC-42a — RoomMember rejoin cùng row:** BAN intent chụp `(roomId=R, memberRow=M, generation=g1)` → pause cleanup → UNBAN → U JOIN lại R, hệ thống tái sử dụng row M nhưng tăng lên `g2` → resume cleanup g1: **không chuyển membership g2 thành LEFT**, không xóa quyền phòng mới; old cleanup idempotent/no-op.
45. **AC-42b — Reservation mới:** BAN intent chụp `reservationId=r1` (và version); pause cleanup → UNBAN → U ACCEPT giao hữu mới tạo `r2` → replay cleanup r1: **r2 vẫn RESERVED/ACTIVE**, cả hai người chơi giữ được slot; không xóa mù theo userId hoặc roomId.
46. **AC-42c — Race / crash / mixed target:** chạy song song cleanup g1/r1 với JOIN g2/ACCEPT r2, rồi restart Server và reconcile lần nữa. Verify DB generation/version + unique/foreign keys, `user_active_game` của Game Y, socket phiên mới, membership/r2 không bị đổi; Game X/phiên cũ vẫn chịu restriction. Không áp dụng intent cũ lên các ID mới theo lối "tra cứu lại người dùng hiện tại".

**Điều kiện PASS:** Assertions **độc lập** trên snapshot scoring, wait set, bảng outcome, điểm, Answer, generation RoomMember, reservation ID/state, pending-intent effect status, session/occupancy, outbound và History; mọi race phải có kiểm thử **cả hai thứ tự commit**. Không thể báo PASS vì UI "có vẻ hoạt động" hoặc chỉ chạy happy path.

**Yêu cầu phương pháp test:** ưu tiên hook/barrier điều khiển transaction/phase thay vì chỉ `Thread.sleep`; xác nhận DB state, command ACK, history, WS outbound và restart recovery. Đây là **test cases cần triển khai**, không phải kết quả đã PASS.

**Khi báo cáo test:** ghi chính xác PASS/FAIL/SKIP; review tĩnh không được mô tả là đã test. Không dùng timing ước lượng thay cho bằng chứng race test.

## 18.13. Phân chia Task để Codex triển khai từng bước

| Bước | Phạm vi | Tiêu chí kết thúc |
|---|---|---|
| **H0 — REVIEW/PLAN** | Đọc auth principal, account/session registry, WS fan-out, Room membership **có rejoin dùng lại row**, `user_active_game`, friendly reservation ID, queue/Answer/**scoring set vs Early Close gate**/**BAN→OPEN fence**/**CLOSED–SCORING**, Game result commit/**award decision commit**, auth session generations và WS outbound writer. Vẽ transaction/lock và chỉ ra khả thi/điểm chặn | **Không sửa code/migration**; báo cáo gap và phương án gọn nhất cho từng mục |
| **H1 — Auth/ADMIN UI** | Role, bootstrap account, login chung redirect theo role, admin API read-only, route `/admin` an toàn | Chứng minh USER/Host không truy cập API ADMIN |
| **H2 — Game restriction/Kick** | Persist marker, Game queue, **DECISION / OPEN / CLOSED / SCORING boundary, scoring set ≠ Early Close set**, occupancy release, **membership-generation-safe room detach**, REST/WS/snapshot filtering/envelope | Race `ACCEPTED_UNSCORED`, early close, outbound/History đạt test; có thể sang Y đúng policy |
| **H3 — Account sanction** | TEMP/INDEFINITE/UNBAN, version/audit/idempotency, **action-scoped session cutoff + targeted Game/membership-generation/reservation intents**, **BAN→OPEN commit fence**, game ejection/reconcile, orphan WAITING | Ban→Unban→session Y/Game Y→old retry không thu hồi nhầm; Host/expiry/restart đạt test |
| **H4 — Award integration & demo** | **Award decision transaction** với eligible-group `CORRECT`, User lock/fence, quyết định bất biến, final ranking vs eligible-award, friendly interruption, regression + LAN | Commit-order/anti-duplicate/crash/retry đạt test, ScoreEngine không đổi |

**Ưu tiên source hiện tại:** nếu phân hệ cúp/award chưa code, H4 chỉ triển khai khi task thưởng đã tồn tại/được giao; không tự kéo phạm vi thành tích vào task Admin. Mỗi Task: nêu file dự kiến sửa, lý do, regression test chạy thật, không tự commit/push hoặc chuyển task sau khi chưa được yêu cầu.

## 18.14. Bốn điểm review trọng yếu Codex cần phản hồi

1. **Kick + occupancy + room membership:** có thể giải phóng ngay một cách nguyên tử/đồng bộ với `START` và giữ được PlayerSession/History không? Nếu không, chỉ rõ transaction nào chưa đủ và sửa tối thiểu.
2. **DECISION/OPEN/CLOSED/SCORING vs next question:** ở đâu chụp participation N và scoring-commit N? Làm sao giữ `ACCEPTED_UNSCORED` qua Kick/Ban sau CLOSE và tránh `NO_ANSWER` khi Kick trong DECISION trước OPEN?
3. **Award eligibility + commit:** ở đâu chốt `eligibleForOfficialAwards`, `CORRECT` thuộc eligible group, winner/huy hiệu cạnh tranh trong nhóm này và `awardDecisionCommit` có User lock/fence? Nếu award chưa có, đề xuất contract/schema tối thiểu, **không** viết lại engine.
4. **Ban Host / orphan Room:** khi Host bị thu hồi quyền, Room ACTIVE tiếp tục và ADMIN có thể đóng Room WAITING theo điều kiện bằng cách nào, không ảnh hưởng phòng khác?

**Bổ sung bắt buộc sau review V3.3 (AC-40–AC-42):** (i) nguồn `scoringSet(N)` độc lập với `QuestionCloseGate` và cách offline vẫn nhận `NO_ANSWER`; (ii) điểm đồng bộ BAN commit–OPEN commit, bao gồm pending intent chưa ejection; (iii) generation khi `RoomMember` rejoin, reservation identity khi ACCEPT mới và conditional cleanup của intent cũ. Codex phải nêu **class/method, transaction, cách lưu/khôi phục sau restart, test barrier và PASS criteria** cho cả ba. Nếu chưa có generation/ID, liệt kê migration tối thiểu, không tự tuyên bố tính năng sẵn có.

**Ngoài bốn điểm trên**, phải chỉ ra chỗ triển khai **revocation theo thế hệ/session snapshot** (không revoke-all mù lúc retry), **Game-targeted intent qua UNBAN**, outbound event envelope/filter và optimistic version/idempotency để **không bỏ sót** lỗ hổng bảo mật hoặc replay ban. Nếu source mâu thuẫn, **dừng ở H0 và trình bày phương án thay thế có trade-off**, không tự đổi luật điểm/Gameplay, không tự chạy migration.

## 18.15. CHECKLIST H0 CHO CODEX — NHỮNG QUYẾT ĐỊNH CẦN XÁC NHẬN TRONG SOURCE

**Không yêu cầu Codex lập trình ngay.** Bản này là đặc tả định hướng triển khai, có các chính sách **KHUYẾN NGHỊ** chưa được người dùng chốt ngưỡng. Codex cần review working tree đang có và phản hồi **PASS VỀ KHẢ THI / BLOCKER / PHƯƠNG ÁN THAY THẾ**, không báo PASS runtime khi chưa chạy test.

| Chủ đề | Điểm Codex cần chỉ ra trong source | Điều kiện chấp nhận thiết kế |
|---|---|---|
| `QUESTION_CLOSED ≠ SCORED` | `GameTransactions` và nơi tạo `ACCEPTED_UNSCORED`, nơi commit SCORING/outcome | Scorer dùng snapshot câu đã OPEN, không bỏ người bị Kick/Ban trước score commit |
| `DECISION` Quiz | Nơi commit Spin/Star/decision và nơi thực sự mở question | Restriction trước OPEN không sinh `NO_ANSWER`; effect tiêu rồi không refund |
| BAN commit fence | AuthService/AuthPrincipal, User lock, Answer transaction, occupancy | Ban/Answer có thứ tự commit rõ, không chỉ check khi nhận request |
| Intent phiên/Game | `AuthSessionRegistry`, `AuthenticatedSocketRegistry`, `user_active_game` | Task cũ chỉ revoke pre-ban sessions, chỉ kick target Game đã snapshot; unban không tạo gap X |
| WebSocket outbound FIFO | `SocketOutbound` writer và TextMessage queue | Filter/drop frame không còn quyền **trước send** theo scope mà không block Game worker/đảo FIFO |
| Award transaction | Game result commit, nơi upsert cúp/stats (nếu chưa tồn tại, ghi contract) | Có `PENDING→DECIDED` bất biến, user-row fence với ban, `CORRECT` eligible-only và unique award |
| Lock hierarchy | Call graph `Room→Game→User`, ban User-only và award Game→ordered Users | Không giữ User lock khi đợi Game queue; race tests chứng minh không deadlock |
| Source V1/V2 | Schema discriminator + luật Score/Ranking theo version | Chỉ award NORMAL V2, không tái tính V1, không đổi Quiz/Spin/Star/mode rules |
| **AC-40: scoring vs Early Close** | `GameLifecycle`, `QuestionCloseGate`, `GameTransactions`, snapshot/outcome | Chứng minh offline ngoài Early Close wait set vẫn chấm `NO_ANSWER` khi thuộc scoring set; không dùng gate làm scoring source |
| **AC-41: ban intent trước OPEN** | Ban commit, target intent lookup, `QUESTION_OPEN` transaction, lock graph | BAN commit trước OPEN dù ejection pause vẫn loại khỏi snapshot N; OPEN commit trước BAN vẫn chấm N |
| **AC-42: membership/reservation generation** | `RoomMember` rejoin row reuse, JOIN transition, friendly reservation/cleanup SQL | Cleanup theo membership generation và reservation ID/version; ban cũ không xóa lần JOIN/ACCEPT mới |

**Các nhận định về source** ở những vòng review trước (ví dụ `GameTransactions.java`, `AuthSessionRegistry.java`, `AuthenticatedSocketRegistry.java`, `SocketOutbound.java`) là **thông tin do Codex báo cáo**, không phải xác minh trực tiếp trong tài liệu. Codex phải nêu **file/line thực tế, biến trạng thái, transaction scope và test dự kiến** sau khi đọc working tree mới nhất.

### Những gì không được tự ý thay đổi

- Luật Quiz +10/−4/−1 và các effect Spin/Star theo rules hiện hành; không reset tài nguyên/điểm giữa 3 màn giao hữu.
- Luật sáu mode còn lại, thưởng câu cuối nếu cấu hình V2 quy định, sàn điểm, ScoreEngine/RankingEngine và lịch sử v1.
- Quy tắc Room WAITING, 1 Server/1 Database, WebSocket send không chặn Game worker.
- Những **ngưỡng thưởng đề xuất** (3 người/10 câu, eligible ≥2, ít nhất một CORRECT eligible, 5 thắng/3 đối thủ cho “Ông vua thách đấu”) **chưa mặc nhiên trở thành quyết định người dùng duyệt**.
- Không tự sửa code, migration hoặc commit/push trong **bước H0 review**.

**Mục tiêu giao Codex:** xác minh khả năng hiện thực V3.4 với thay đổi nhỏ nhất và tạo kế hoạch theo H0–H4. Chỉ sau khi các ngưỡng/chính sách được người dùng đồng ý mới biến phần đề xuất thành acceptance criteria của task code tương ứng.


**Kết luận ADMIN V3.4:** Một trang đăng nhập chung, Server cấp quyền `/admin` theo role; Kick theo Game, Ban theo tài khoản. Quyền Answer/scoring phân biệt phase OPEN/CLOSED/SCORING; tác vụ ban ràng buộc đúng action/phiên/Game; cúp được quyết định theo eligible group tại **award-decision commit**. **Scoring set khác Early Close wait set; ban intent đã commit phải chặn tham gia ngay tại OPEN; cleanup Room/reservation ràng buộc đúng generation/ID.** Không thay luật Quiz/giao hữu, không cho phép retry cũ ảnh hưởng phiên/trận/lượt JOIN/giữ chỗ mới.
