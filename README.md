```
src/main/java/com/example/sqlexecutor/
├── SqlExecutorApplication.java
├── controller/
│   ├── SqlController.java
│   └── SqlHistoryController.java
├── model/
│   ├── ColumnInfo.java
│   ├── PageRequest.java
│   ├── SqlQuery.java
│   └── SqlResult.java
├── service/
│   ├── SqlExecutorService.java
│   └── impl/
│       └── SqlExecutorServiceImpl.java
├── exception/
│   └── SqlExecutionException.java
├── validator/
│   └── ConfirmationValidator.java
└── config/
    └── SqlLogger.java
```
```
src/
├── app/
│   ├── app.component.ts
│   ├── app.component.html
│   ├── app.module.ts
│   ├── confirmation-modal/
│   │   ├── confirmation-modal.component.ts
│   │   ├── confirmation-modal.component.html
│   │   └── confirmation-modal.component.css
│   ├── interceptors/
│   │   └── timeout.interceptor.ts
│   ├── models/
│   │   ├── sql-query.model.ts
│   │   ├── sql-result.model.ts
│   │   └── sql-history.model.ts
│   ├── services/
│   │   └── sql.service.ts
│   └── sql-editor/
│       ├── sql-editor.component.ts
│       ├── sql-editor.component.html
│       └── sql-editor.component.css
├── assets/
├── environments/
└── index.html
```


Hướng dẫn chạy ứng dụng

```
Chạy Backend
bash# Cấu hình cơ sở dữ liệu trong application.properties trước khi chạy
cd sql-executor-backend
./mvnw spring-boot:run

Chạy Frontend
bashcd sql-executor-frontend
npm install
ng serve
```

Truy cập ứng dụng tại: http://localhost:4200
