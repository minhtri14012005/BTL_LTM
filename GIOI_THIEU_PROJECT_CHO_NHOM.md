# Trò chơi nhiều chế độ qua mạng — tài liệu giới thiệu cho nhóm

> **Phạm vi của tài liệu:** Bản này diễn giải các luật chơi và lựa chọn kiến trúc mà nhóm đã chốt. Nguồn là các trao đổi về gameplay và báo cáo khảo sát code ngày 09/10/2026. Nhóm vừa tiếp tục sửa project sau thời điểm đó; trước khi dùng bản này để khẳng định một tính năng **đã chạy**, cần đối chiếu source và demo mới nhất. Các ví dụ bên dưới là ví dụ minh họa luật, không phải kết quả thử nghiệm.

## 1. Project của chúng ta là gì?

Đây là trò chơi thi đấu cá nhân nhiều người qua mạng nội bộ. Một người tạo phòng (**Host**), chọn các màn chơi và bộ câu hỏi. Những người còn lại vào phòng bằng trình duyệt trên máy tính hoặc điện thoại cùng mạng Wi-Fi. Tất cả cùng thấy một câu tại cùng giai đoạn, gửi đáp án của riêng mình và cạnh tranh trên **một bảng xếp hạng xuyên suốt trận**.

Trận có thể chỉ chọn một chế độ hoặc chọn tối đa bảy chế độ, không bắt buộc phải chơi đủ. Quiz bốn lựa chọn là một chế độ trong số đó. Mỗi màn có một bộ câu hỏi đúng loại, số câu và thời gian trả lời do Host cấu hình; tổng số câu của trận tối đa 50. Mục tiêu là giải trí: ở luật mới người chơi bắt đầu từ 0 điểm, điểm không xuống dưới 0 và không bị loại chỉ vì trả lời sai.

Về công nghệ, project dùng **Web Client → REST/WebSocket → một Spring Boot Server → một MySQL Database**. Server chạy trên một máy. Khi demo trên LAN, các Client truy cập địa chỉ IP nội bộ của máy đó; không có Cloud hoặc nhiều Server. Việc có thể truy cập qua cùng Wi-Fi là mục tiêu thiết kế; demo đa thiết bị chỉ được gọi là đã kiểm chứng khi nhóm thực sự chạy trên các máy khác nhau.

## 2. Ai làm gì trong trò chơi?

| Vai trò | Việc có thể làm |
|---|---|
| Người tạo bộ câu | Tạo câu hỏi thuộc một chế độ, đặt đáp án hợp lệ và chọn PUBLIC hoặc PRIVATE. Video bài hát do người tạo cắt sẵn rồi upload; ảnh/video được Server lưu local. |
| Host | Tạo Room, chọn thứ tự các chế độ, một bộ câu cho từng màn, số câu và thời gian; quản lý phòng, bắt đầu hoặc hủy trận theo quyền Room. Host có thể tham gia với tư cách Player hoặc quan sát nếu quyền và bộ câu cho phép. |
| Player | Vào phòng, bấm sẵn sàng ở phần giới thiệu màn, xem câu, trả lời và xem điểm của mình cùng bảng xếp hạng. Mỗi người có một Answer riêng; không phải cả phòng cùng nộp một đáp án. |
| Host spectator | Quản lý Room và xem trận nhưng không phải một Player cần trả lời hoặc bấm Tiếp tục để mở màn sớm. |

Trong thư viện bộ câu, **Của tôi** gồm các bộ PUBLIC và PRIVATE do mình tạo. **Chung** gồm mọi bộ PUBLIC, kể cả PUBLIC của mình. PRIVATE của người khác không hiện trong danh sách khám phá. Một bộ PRIVATE được Host có quyền đưa vào Room vẫn có thể được những Player được mời chơi; quyền chọn/sửa bộ câu và quyền tham gia trận là hai chuyện khác nhau. Theo policy bảo toàn từ Quiz cũ, tác giả của bộ được chọn không tham gia trận với vai trò Player, nhưng có thể Host spectator. Nhóm nên kiểm tra lại chính sách này trên bản code mới nhất khi demo nhiều bộ.

Khi Start, Server chụp **snapshot** của cấu hình và nội dung dùng cho trận. Nhờ vậy tác giả sửa bộ câu sau đó không làm câu đang chơi hoặc lịch sử trận đã kết thúc thay đổi.

## 3. Bảy chế độ chơi

| Chế độ | Trên màn hình | Người chơi trả lời như thế nào? | Ví dụ minh họa |
|---|---|---|---|
| **Quiz** | Câu hỏi và bốn lựa chọn A/B/C/D. Trước câu có thời gian chọn chiến thuật. | Chọn một đáp án; có thể dùng Spin hoặc Hope Star theo tài nguyên còn lại. | “Thủ đô Việt Nam là đâu?” → chọn Hà Nội. |
| **Đoán tên bài hát** | Đoạn video ngắn có hình và âm thanh do tác giả đưa lên. | Gõ tên bài hát; có thể chấp nhận các tên gọi khác đã được tác giả khai báo. | Xem/nghe một đoạn và nhập tên bài hát. |
| **Vua Tiếng Việt** | Những chữ hoặc mảnh chữ bị xáo trộn. | Sắp chúng thành một từ hoặc cụm từ có nghĩa rồi gửi kết quả. | Ghép các mảnh thành “đậu phộng”. |
| **Đố mẹo** | Một câu đố bằng chữ. | Gõ lời giải. | “Cái gì càng lấy đi càng lớn?” → “cái hố”, nếu được đặt là đáp án. |
| **Truy tìm dấu vết** | Câu đố và các gợi ý xuất hiện lần lượt theo thời gian chung. | Đọc các dấu vết đã mở và gõ đáp án; không phải chờ đủ gợi ý mới được trả lời. | Đoán một địa danh từ các gợi ý được công bố dần. |
| **Đuổi hình bắt chữ** | Một hình ảnh gợi một từ hoặc cụm từ. | Quan sát hình rồi nhập chữ. | Hình gợi thành ngữ; đáp án là thành ngữ, không nhất thiết là tên đồ vật trong ảnh. |
| **Sắp xếp trình tự** | Một số mục đang ở sai thứ tự. | Sắp **toàn bộ** các mục đúng thứ tự và nộp. | Sắp các bước của một quá trình theo thứ tự xảy ra. |

Với đáp án nhập chữ, hệ thống so khớp **có dấu tiếng Việt**, không phân biệt chữ hoa/thường, bỏ khoảng trắng đầu/cuối và gộp nhiều khoảng trắng liên tiếp. Không tự bỏ dấu hoặc đoán “gần đúng”. Tác giả có thể định nghĩa nhiều đáp án hợp lệ, chẳng hạn tên gọi vùng miền. Các chế độ sắp xếp phải gửi đủ phần tử, không có phần tử lạ hoặc trùng; chế độ Trình tự chỉ đúng khi cả thứ tự đúng.

## 4. Từ lúc tạo phòng đến khi hết trận

1. Người dùng đăng ký/đăng nhập. Người tạo chuẩn bị bộ câu hỏi theo từng chế độ, chọn PUBLIC hoặc PRIVATE.
2. Host tạo Room, chọn một đến bảy chế độ khác nhau theo thứ tự, một bộ câu đúng loại cho mỗi màn, số câu và thời gian trả lời từng màn. Player khác tham gia Room.
3. Host bấm Start. Server kiểm tra người chơi, quyền dùng bộ câu và nội dung, tạo Game cùng snapshot. Mọi người cùng vào màn giới thiệu của chế độ đầu tiên.
4. Màn giới thiệu tối đa **10 giây**. Nếu mọi Player bấm **Tiếp tục**, Server mở màn sớm; Host spectator không giữ cả phòng chờ. Nếu còn Player chưa bấm, Server tự mở khi hết giờ. Giới thiệu chỉ lặp lại lúc đổi chế độ, không lặp giữa các câu của cùng màn.
5. **Riêng Quiz:** trước mỗi câu có **7 giây** để chọn Spin, Hope Star hoặc chơi thường. Sau đó Server mở câu hỏi cho cả phòng. Các chế độ còn lại vào câu hỏi theo flow của màn mà không có Spin/Star.
6. Mỗi Player gửi tối đa một Answer hợp lệ cho câu. Server đóng câu khi điều kiện mọi người cần chờ đã trả lời, hoặc hết thời gian trả lời của màn. Server chấm và công bố đáp án, biến động điểm và bảng xếp hạng trong giai đoạn kết quả chung khoảng **1,5 giây**.
7. Server tự mở câu kế tiếp, màn giới thiệu chế độ kế tiếp hoặc kết quả cuối trận. Không cần Host bấm chuyển câu. Hủy trận là thao tác quyền Host; lịch sử trận đã lưu vẫn có thể xem theo chức năng hiện có.

**Ví dụ:** Room chọn Quiz 5 câu rồi Đố mẹo 3 câu. Cả phòng xem giới thiệu Quiz; trước từng câu Quiz có 7 giây quyết định chiến thuật. Hết câu thứ năm, cả phòng xem giới thiệu Đố mẹo tối đa 10 giây. Ba câu Đố mẹo dùng cùng tổng điểm đã có từ Quiz. Hết câu thứ ba, Server kết thúc và xếp hạng toàn trận.

## 5. Điểm và bảng xếp hạng

### 5.1. Sáu chế độ ngoài Quiz

Trả lời **đúng: +10**. Riêng **câu cuối của mỗi màn không phải Quiz**, đúng được **+20**. Trả lời sai hoặc không trả lời: **+0**, không bị trừ điểm. Câu cuối Quiz không có thưởng nhân đôi. Các màn cộng vào cùng một tổng điểm.

Ví dụ màn Đố mẹo có ba câu: đúng câu đầu được 10 điểm, sai câu thứ hai được 0, đúng câu thứ ba được 20. Tổng màn đó thêm **30 điểm**.

### 5.2. Quiz, Spin và Hope Star

Quiz thông thường đúng **+10**, sai **−4**, không trả lời **−1** trước khi xét effect. Spin quay ngẫu nhiên một effect theo trọng số của luật Quiz; người chơi không được quay lại chỉ vì không thích effect. Số lượt Spin mỗi người là `floor(số câu Quiz trong toàn trận / 10)` và chỉ tiêu tại Quiz: 9 câu Quiz có 0 lượt, 10 câu có 1 lượt, 20 câu có 2 lượt. Nếu trận có Quiz, mỗi người tối đa một Hope Star; trận không chọn Quiz thì không cấp cả Spin lẫn Star.

Star dùng riêng hoặc thêm sau Spin khi effect cho phép. Nếu đã dùng Star riêng trước, không Spin tiếp trong cùng câu; HARDSHIP không được cộng Star. Bảng điểm chốt của từng cách chơi:

| Cách chơi Quiz | Đúng | Sai | Không trả lời |
|---|---:|---:|---:|
| Thường | +10 | −4 | −1 |
| Star riêng | +25 | −14 | −7 |
| BONUS | +15 | −4 | −1 |
| BONUS + Star | +30 | −4 | −1 |
| SAFE | +8 | −2 | 0 |
| SAFE + Star | +20 | −2 | 0 |
| BREAKTHROUGH | +18 | −7 | −3 |
| BREAKTHROUGH + Star | +30 | −12 | −6 |
| SPEED | +22 | −10 | −4 |
| SPEED + Star | +35 | −16 | −8 |
| DECISIVE | +28 | −15 | −7 |
| DECISIVE + Star | +40 | −22 | −10 |
| HARDSHIP | +8 | −6 | −2 |

Spin có sáu effect BONUS/SAFE/BREAKTHROUGH/SPEED/DECISIVE/HARDSHIP với trọng số lần lượt **25/20/25/15/10/5**; đây là xác suất tương đối, không phải sáu nút để người chơi tự chọn.

**Streak:** năm câu Quiz đúng liên tiếp cấp Momentum; năm câu Quiz sai liên tiếp cấp Recovery. Đúng cắt chuỗi sai, sai cắt chuỗi đúng, không trả lời cắt cả hai. Momentum cộng thêm **+3** vào lần đúng phù hợp tiếp theo; Recovery giảm một khoản phạt gốc âm bằng `min(basePenalty + 3, 0)` và bị tiêu dù khoản phạt sau giảm bằng 0. Nếu base penalty là 0 hoặc đáp án đúng thì Recovery được giữ. Effect vừa được cấp không tác động ngược chính câu tạo streak; effect không chồng vô hạn.

Điểm của trận nhiều chế độ **không bao giờ âm**. Ví dụ đang 0 điểm và Quiz thường sai (phạt −4), điểm vẫn 0; thông báo cho Player phải thể hiện biến động thực tế là **0**, không nói đã mất 4 điểm. Người chơi không bị loại. Trận Quiz phiên bản cũ trong History có thể đã bắt đầu 20 điểm và loại người âm điểm; đó là luật của bản cũ, không dùng để giải thích trận nhiều chế độ mới.

### 5.3. Nếu bằng điểm thì ai xếp trên?

Server xếp theo **tổng điểm cao hơn**. Nếu bằng điểm, so **tổng thời gian trả lời những câu ĐÚNG**: ai ít thời gian hơn đứng trên. Câu sai, câu bỏ trống, thời gian chọn chiến thuật, màn giới thiệu và chờ kết quả không cộng vào chỉ số này. Nếu điểm và thời gian đúng đều bằng nhau thì đồng hạng. Thời gian tính trên Server từ lúc mở câu đến lúc Server nhận Answer hợp lệ; Client không tự khai thời gian để được xếp cao hơn.

Ví dụ A và B cùng 40 điểm. A có tổng thời gian của các câu đúng 28 giây, B là 34 giây: A xếp trên. Nếu cả hai đều 28 giây theo độ chính xác hệ thống dùng, họ đồng hạng.

## 6. Client làm gì, Server làm gì?

| Web Client trên máy từng người | Spring Boot Server trên máy chủ |
|---|---|
| Hiển thị đăng nhập, thư viện bộ câu, Room, màn giới thiệu, câu hỏi, lựa chọn/ô nhập/sắp xếp, media, đồng hồ, điểm và bảng xếp hạng. | Xác thực tài khoản và quyền; quản lý Room, danh sách Player, trận, thứ tự màn và snapshot câu hỏi. |
| Gửi thao tác tạo phòng, chọn bộ, Start, Continue, Spin/Star, Answer, reconnect; hiển thị thông báo Server trả về. | Gán deadline chung, kiểm tra Answer đến kịp không, tính effect và điểm, xếp hạng, ghi MySQL rồi công bố kết quả. |
| Phát video tại vị trí phù hợp với thời điểm câu đã mở; hiển thị clue chỉ sau khi Server công bố. | Quyết định clue nào đã được mở, tài nguyên nào còn, yêu cầu nào trùng, Answer nào được nhận. |

**REST** là cách trình duyệt gửi những yêu cầu quản lý tài khoản, bộ câu, phòng và đọc dữ liệu theo từng lần gọi. **WebSocket** là kết nối hai chiều đang mở để gửi command của trận và nhận thay đổi giai đoạn, deadline, kết quả gần như ngay lập tức. Ảnh/video là tệp được Client tải từ Server bằng HTTP; WebSocket mang thông tin cần đồng bộ, không truyền toàn bộ video qua mỗi sự kiện.

Quy tắc dễ nhớ khi bảo vệ: **Client trình bày, Server quyết định**. Không thể tự sửa điểm trong giao diện để Server công nhận; Server dùng danh tính phiên đăng nhập và thời điểm nhận yêu cầu của chính mình. MySQL giữ dữ liệu tài khoản, bộ câu, snapshot và kết quả cần lưu. `@Entity`/Repository là cách Spring Data JPA làm việc với những bảng đó; WebSocket không thay MySQL.

## 7. Vì sao nhiều người vẫn thấy cùng một trận?

Mỗi Player có một tab trình duyệt và kết nối mạng riêng. Tất cả gửi về **cùng một Game trên Server**. Server đặt mốc mở và đóng câu chung, nhận các yêu cầu, tuần tự hóa thao tác thuộc một trận, chấm điểm một lần, lưu thành công rồi phát trạng thái mới. Hai Game khác nhau có thể tiến triển đồng thời mà không cần một hàng đợi duy nhất cho toàn bộ hệ thống.

Ví dụ ở sát deadline, A và B cùng bấm Gửi: Server xét thời điểm ingress chính thức so với deadline và thứ tự xử lý của trận; request `time < deadline` hợp lệ, `time >= deadline` bị từ chối. Một Answer đã nhận không bị chấm hai lần chỉ vì Client gửi lại sau khi mất ACK. `requestId` giúp nhận diện lần thử lại và trả lại phản hồi thích hợp. Timer cũ của câu trước không được đóng hoặc chấm câu mới.

Đây là lý do project có cơ chế **đồng thời, timeout, lỗi và phục hồi kết nối**, chứ không chỉ là trang hiển thị câu hỏi. Những bảo đảm cụ thể sau khi đổi sang bảy chế độ cần được nhóm kiểm tra lại bằng test và source mới nhất trước khi tuyên bố đã nghiệm thu.

## 8. Mất mạng và quay lại

Server **không tạm dừng trận** theo một Client. Nếu câu kéo dài 20 giây, một người rớt mạng ở giây thứ 5 và trở lại sau 10 giây, họ còn khoảng 5 giây. Nếu lúc trở lại phòng đã ở câu khác, họ nhận câu hiện tại, không nhận lại 20 giây của câu đã bỏ lỡ. Answer đã được Server xác nhận vẫn được lưu; điểm và tài nguyên Spin/Star không tự reset.

Đóng câu sớm V2 xét theo từng đợt mất kết nối: **câu đầu của đợt vẫn chờ**, kể cả Player offline trước lúc câu mở. Nếu họ còn offline khi **câu kế tiếp** mở thì không giữ phòng chờ ở câu đó. Reconnect kết thúc đợt; mất kết nối lại tạo đợt mới. Tập chờ của câu đang mở không thay đổi; Player ngoài tập vẫn nộp được nếu Answer vào Game queue trước Close và deadline. Câu đã đóng không được chơi lại, reconnect không thêm thời gian. Host spectator không thuộc tập chờ; điểm/tài nguyên/lịch sử được giữ. Tập chờ rỗng vẫn chờ deadline. Tất cả mọi người tiếp tục theo một nhịp chung.

Ở màn giới thiệu, người trở lại lúc giây thứ 8 của khoảng 10 giây chỉ còn khoảng 2 giây. Với câu video 25 giây, quay lại tại giây thứ 15 thì còn khoảng 10 giây; Client phát từ vị trí phù hợp, không bắt đầu video từ đầu để được nghe thêm. Nếu trình duyệt chặn phát âm thanh tự động, giao diện cần nút phát/bật âm thanh dự phòng; deadline không đổi. Nếu file ảnh/video trên Server tải lỗi, người chơi thấy thông báo lỗi nhưng vẫn có thể đoán; câu không bị bỏ riêng cho họ.

## 9. Nếu muốn đọc code, bắt đầu ở đâu?

Trong báo cáo khảo sát trước lần mở rộng, source được chia theo feature `auth`, `user`, `quiz`, `room`, `game`, `realtime`, cùng frontend tĩnh dưới `src/main/resources/static/client`. Tên base package và application class có thể đã được đổi khi nhóm đặt lại tên project; dùng **tên hiện tại trong IDE** làm chuẩn. Trình tự đọc gợi ý:

| Nơi tìm | Câu hỏi nên trả lời khi đọc |
|---|---|
| `auth`, `user` | Server biết người gửi yêu cầu là ai? Đăng nhập, session, CSRF và quyền được kiểm tra ở đâu? |
| `quiz` và phần nội dung các mode | Bộ câu, câu hỏi, đáp án đúng và quyền PUBLIC/PRIVATE được tạo, lưu, đọc thế nào? |
| `room` | Host cấu hình màn, người chơi vào phòng và Start tranh chấp với Join/Leave ra sao? |
| `game` | Snapshot, phase, chấm điểm, xếp hạng, kết thúc và History được xử lý ở đâu? |
| `realtime` | WebSocket nhận command, đóng gói sự kiện, queue, timer, replay và reconnect làm gì? |
| `static/client` | Giao diện chọn màn, nhận event và vẽ câu hỏi/bảng điểm ở đâu? |

Một luồng đáng học nhất để bảo vệ: **Player gửi Answer → Server gắn thời điểm nhận → đưa vào queue của Game → kiểm tra phase/deadline → chấm bằng engine → lưu transaction → phát kết quả → các Client cập nhật UI**. Sau đó thử theo cùng luồng khi Player mất mạng đúng lúc gửi, để hiểu vì sao cần requestId, replay và snapshot.

## 10. Kịch bản cho cả nhóm tập trình bày

Host tạo Room gồm Quiz và hai chế độ khác, ba Player vào từ các trình duyệt khác nhau. Host giải thích cấu hình từng màn; một thành viên nói rõ REST dùng khi tạo phòng, WebSocket dùng khi trận chuyển giai đoạn. Ở Quiz, một Player dùng Spin, người khác chơi thường; nhóm quan sát điểm thay đổi. Sang chế độ nhập chữ, cả phòng cùng thấy câu và Server chấm từng người. Một Player thử ngắt kết nối rồi vào lại giữa câu để chỉ ra timer không được cấp lại. Cuối trận, xem bảng xếp hạng và History. Chỉ mô tả đây là **demo LAN nhiều máy đã chạy** nếu nhóm thực sự dùng các thiết bị khác nhau và ghi nhận kết quả.

### Những điều cần đối chiếu trước khi dùng bản này để bảo vệ

- Kiểm tra bảy chế độ nào đã hoàn thành cả editor, gameplay, reconnect và History trên bản source hiện tại; đánh dấu phần chưa hoàn thành trong bản trình bày.
- Kiểm tra tên package/class sau lần đổi tên project, công thức điểm và thời gian trên ScoreEngine/RankingEngine, thời gian phase trong cấu hình thực tế.
- Chỉ nêu số test, số liệu thực nghiệm và demo nhiều máy khi có log/CSV hoặc biên bản chạy đúng phiên bản. Số liệu loopback của Quiz cũ không đại diện cho bản bảy chế độ.
- Đối chiếu riêng tài liệu Topics/Instruction của thầy khi nhóm viết báo cáo nộp môn; bản này là **tài liệu giải thích project cho đồng đội**, không thay báo cáo môn hoặc bằng chứng thực nghiệm.
