package baosys.manager.app.services;

public record LoginResult(
        String accessToken,
        String refreshToken,
        String csrfToken) {
}
