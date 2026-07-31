package com.ok.websocket.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import com.ok.auth.service.JwtService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StompJwtChannelInterceptorDemo
        implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String STUDENT_APP_PREFIX =
            "/app/student/exam-attempts/";
    private static final String STUDENT_USER_QUEUE_PREFIX =
            "/user/queue/exam-attempts/";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor = MessageHeaderAccessor
                .getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT) {
            authenticate(accessor);
            return message;
        }

        if (command == StompCommand.DISCONNECT) {
            return message;
        }

        if (accessor.getUser() == null) {
            throw new AccessDeniedException(
                    "WebSocket session chưa được xác thực"
            );
        }

        if (command == StompCommand.SEND) {
            requireDestinationPrefix(
                    accessor.getDestination(),
                    STUDENT_APP_PREFIX
            );
        }

        if (command == StompCommand.SUBSCRIBE) {
            requireDestinationPrefix(
                    accessor.getDestination(),
                    STUDENT_USER_QUEUE_PREFIX
            );
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(
                "Authorization"
        );

        if (authorization == null) {
            authorization = accessor.getFirstNativeHeader(
                    "authorization"
            );
        }

        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException(
                    "Thiếu JWT trong WebSocket CONNECT"
            );
        }

        try {
            String token = authorization.substring(
                    BEARER_PREFIX.length()
            );
            String email = jwtService.extractEmail(token);
            UserDetails userDetails = userDetailsService
                    .loadUserByUsername(email);

            if (!jwtService.isTokenValid(
                    token,
                    userDetails.getUsername()
            )) {
                throw new AccessDeniedException(
                        "JWT WebSocket không hợp lệ hoặc đã hết hạn"
                );
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            accessor.setUser(authentication);
        } catch (AccessDeniedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AccessDeniedException(
                    "Không thể xác thực JWT WebSocket",
                    exception
            );
        }
    }

    private void requireDestinationPrefix(
            String destination,
            String allowedPrefix
    ) {
        if (destination == null
                || !destination.startsWith(allowedPrefix)) {
            throw new AccessDeniedException(
                    "WebSocket destination không được phép"
            );
        }
    }
}
