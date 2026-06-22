package com.nbys.activity.dto;

public class ApiResponse<T> {
    public int code;
    public String message;
    public T data;

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<T>(0, "ok", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<T>(code, message, null);
    }
}
