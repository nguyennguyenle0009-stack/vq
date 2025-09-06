gamevq — Java Gradle multi-module (client / server / lib)

Dự án khởi tạo cho game tiên hiệp theo mô hình client–server.
Tách thành 3 module để dễ quản lý và đóng gói độc lập:

gamevq/
├─ client/   # Game client (UI, input, networking)
├─ server/   # Game server (DB, logic authoritative)
└─ lib/      # Mã dùng chung (model, DTO, rule tính chỉ số, v.v.)


JDK: 17 • Build: Gradle (Kotlin DSL) • IDE: Eclipse

1) Yêu cầu môi trường

JDK 17 (Temurin/OpenJDK).

Gradle Wrapper đã có sẵn (gradlew, gradlew.bat), không cần cài Gradle.

(Tuỳ chọn) Git + Git LFS nếu có asset nặng (ảnh/âm thanh).

2) Cấu trúc thư mục tiêu chuẩn Gradle

Mỗi module đều theo chuẩn:

<module>/
└─ src/
   ├─ main/
   │  ├─ java/       # code chạy thật (bắt buộc)
   │  └─ resources/  # tài nguyên đi kèm jar (ảnh, cấu hình, SQL, ...)
   └─ test/
      ├─ java/       # test tự động (không bắt buộc lúc đầu)
      └─ resources/  # tài nguyên dùng riêng cho test
      