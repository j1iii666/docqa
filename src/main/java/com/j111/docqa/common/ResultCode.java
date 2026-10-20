package com.j111.docqa.common;

/**
 * 业务返回码。
 * <p>
 * 约定：响应体里的 code 才是业务码；HTTP 状态码统一 200（网关/前端只看 body）。
 */
public enum ResultCode {

    SUCCESS(200, "成功"),

    PARAM_INVALID(400, "参数不合法"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    NOT_FOUND(404, "资源不存在"),
    RATE_LIMITED(429, "请求太频繁，请稍后再试"),

    INTERNAL_ERROR(500, "服务器内部错误"),

    USERNAME_TAKEN(1001, "用户名已被占用"),
    LOGIN_FAILED(1002, "用户名或密码错误"),

    UNSUPPORTED_FILE(1003, "不支持的文件类型，只支持 pdf / docx / doc / txt"),
    EMPTY_FILE(1004, "文件内容为空"),
    FILE_TOO_LARGE(1005, "文件超过大小限制"),
    DOC_PARSE_FAILED(1006, "文档解析失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
