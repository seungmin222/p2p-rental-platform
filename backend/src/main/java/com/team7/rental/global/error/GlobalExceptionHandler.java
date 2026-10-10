package com.team7.rental.global.error;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.response.FieldErrorDetail;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;

/**
 * 모든 예외를 팀 공통 응답 형식으로 바꾼다. 응답 메시지에 내부 예외 메시지를 노출하지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String INVALID_FORMAT = "형식이 올바르지 않습니다.";
	private static final String REQUIRED = "필수 값입니다.";
	private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		log.info("BusinessException: {}", e.getErrorCode().getCode());
		return toResponse(e.getErrorCode(), List.of());
	}

	/** 컨트롤러 안의 인증 예외(메서드 보안 등). 처리하지 않으면 아래 Exception 핸들러에 걸려 500이 된다. */
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
		return toResponse(ErrorCode.AUTH_UNAUTHORIZED, List.of());
	}

	/** 메서드 보안(@PreAuthorize) 거부. 비회원이면 403이 아니라 401(로그인 필요)이다. */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		boolean anonymous = authentication == null || TRUST_RESOLVER.isAnonymous(authentication);
		return toResponse(anonymous ? ErrorCode.AUTH_UNAUTHORIZED : ErrorCode.AUTH_FORBIDDEN, List.of());
	}

	/** @Valid @RequestBody, @ModelAttribute 검증 실패 (MethodArgumentNotValidException 포함) */
	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiResponse<Void>> handleBind(BindException e) {
		return toResponse(ErrorCode.COMMON_INVALID_INPUT, toDetails(e.getFieldErrors()));
	}

	/** 컨트롤러 파라미터(@RequestParam, @PathVariable 등)의 제약 위반 */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException e) {
		List<FieldErrorDetail> details = new ArrayList<>();
		for (ParameterValidationResult result : e.getParameterValidationResults()) {
			if (result instanceof ParameterErrors errors) {
				details.addAll(toDetails(errors.getFieldErrors()));
				continue;
			}
			String field = result.getMethodParameter().getParameterName();
			for (MessageSourceResolvable error : result.getResolvableErrors()) {
				details.add(new FieldErrorDetail(field, error.getDefaultMessage()));
			}
		}
		return toResponse(ErrorCode.COMMON_INVALID_INPUT, details);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return toResponse(ErrorCode.COMMON_INVALID_INPUT, List.of(new FieldErrorDetail(e.getName(), INVALID_FORMAT)));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
		return toResponse(ErrorCode.COMMON_INVALID_INPUT,
			List.of(new FieldErrorDetail(e.getParameterName(), REQUIRED)));
	}

	/** JSON 문법 오류, 필드 타입·형식 불일치(날짜·Enum 등) */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
		if (e.getCause() instanceof JacksonException cause && !cause.getPath().isEmpty()) {
			return toResponse(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail(toFieldPath(cause.getPath()), INVALID_FORMAT)));
		}
		return toResponse(ErrorCode.COMMON_INVALID_INPUT, List.of());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		return toResponse(ErrorCode.COMMON_METHOD_NOT_ALLOWED, List.of());
	}

	@ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
	public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
		return toResponse(ErrorCode.COMMON_NOT_FOUND, List.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		log.error("Unhandled exception", e);
		return toResponse(ErrorCode.COMMON_INTERNAL_ERROR, List.of());
	}

	private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, List<FieldErrorDetail> details) {
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode, details));
	}

	private List<FieldErrorDetail> toDetails(List<FieldError> fieldErrors) {
		return fieldErrors.stream()
			.map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
			.toList();
	}

	/** Jackson 경로를 "items[0].startDate" 형태로 만든다. */
	private String toFieldPath(List<JacksonException.Reference> path) {
		String joined = path.stream()
			.map(ref -> ref.getPropertyName() != null ? "." + ref.getPropertyName() : "[" + ref.getIndex() + "]")
			.collect(Collectors.joining());
		return joined.startsWith(".") ? joined.substring(1) : joined;
	}
}
