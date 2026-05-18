package com.hify.common.exception;

import com.hify.common.web.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        ErrorCode ec = e.getErrorCode();
        String detail = e.getMessage();
        String message = detail.equals(ec.getMessage()) ? ec.getMessage() : ec.getMessage() + " - " + detail;
        return Result.fail(ec.getCode(), message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ErrorCode ec = ErrorCode.PARAM_ERROR;
        return Result.fail(ec.getCode(), ec.getMessage() + " - " + detail);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return Result.fail(ec.getCode(), ec.getMessage());
    }
}
