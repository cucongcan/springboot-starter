package com.yourcompany.starter.common.exception;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ResourceNotFoundException e) {
            return error(env, e.getMessage(), "NOT_FOUND", ErrorType.DataFetchingException);
        }
        if (ex instanceof UnauthorizedException e) {
            return error(env, e.getMessage(), "UNAUTHORIZED", ErrorType.DataFetchingException);
        }
        if (ex instanceof AccessDeniedException) {
            return error(env, "Access denied", "ACCESS_DENIED", ErrorType.DataFetchingException);
        }
        if (ex instanceof BadCredentialsException || ex instanceof UsernameNotFoundException) {
            return error(env, "Invalid username or password", "INVALID_CREDENTIALS", ErrorType.DataFetchingException);
        }
        if (ex instanceof IllegalArgumentException e) {
            return error(env, e.getMessage(), "BAD_REQUEST", ErrorType.ValidationError);
        }
        // Unhandled — log và để framework xử lý (ra INTERNAL_ERROR)
        log.error("Unhandled GraphQL exception at field [{}]", env.getField().getName(), ex);
        return null;
    }

    private GraphQLError error(DataFetchingEnvironment env, String message,
                               String code, ErrorType type) {
        return GraphqlErrorBuilder.newError(env)
                .message(message)
                .errorType(type)
                .extensions(java.util.Map.of("code", code))
                .build();
    }
}
