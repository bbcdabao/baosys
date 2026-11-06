package baosys.manager.infra.security;

import baosys.manager.api.module.responses.SystemDefinition;
import baosys.manager.app.config.SystemCatalog;
import baosys.manager.app.error.BusinessException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 子系统目录是浏览器回跳的唯一白名单；导航权限不替代网关和接口鉴权。
 */
@Component
public class SubsystemNavigation {
    private record Entry(String type, URI base, String home) {}
    private final URI manager;
    private final List<Entry> entries;
    private final URI managerUiBase;

    public SubsystemNavigation(SystemCatalog catalog, SharedLoginProperties properties) {
        manager = URI.create(properties.managerOrigin());
        entries = catalog.systems().stream().map(this::entry).toList();
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                if (contains(entries.get(i).base(), entries.get(j).base())
                    || contains(entries.get(j).base(), entries.get(i).base())) {
                    throw new IllegalArgumentException("Subsystem UI URL ranges must not overlap");
                }
            }
        }
        var mgr = entries.stream().filter(e -> e.type().equals("MGR")).findFirst().orElseThrow();
        managerUiBase = mgr.base();
        if (!sameHostAndScheme(managerUiBase, manager) || port(managerUiBase) != port(manager)) {
            throw new IllegalArgumentException("MGR ui-base-url origin must equal manager-origin");
        }
    }

    private Entry entry(SystemDefinition system) {
        try {
            URI base = URI.create(system.uiBaseUrl());
            validateSyntax(base);
            if (!base.isAbsolute() || !sameHostAndScheme(base, manager)
                || base.getRawQuery() != null || base.getRawFragment() != null
                || !base.getPath().endsWith("/")) {
                throw new IllegalArgumentException();
            }
            String path = system.homePath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                throw new IllegalArgumentException();
            }
            validateSyntax(URI.create(base.toASCIIString() + path.substring(1)));
            URI home = base.resolve(path.substring(1));
            validateSyntax(home);
            if (!contains(base, home) || reserved(home, base)) throw new IllegalArgumentException();
            return new Entry(system.systemType(), base, home.toASCIIString());
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid UI navigation configuration: " + system.systemType(), invalid);
        }
    }

    public String loginUrl() {
        return managerUiBase.resolve("login").toASCIIString();
    }

    public String authorizedSystemsUrl() {
        return managerUiBase.resolve("authorized-systems").toASCIIString();
    }

    /**
     * 空地址表示登录后使用账户必填默认系统；相对地址仅允许从 Manager 根路径解析。
     */
    public String validateReturnUrl(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute()) {
                if (!value.startsWith("/") || value.startsWith("//")) throw new IllegalArgumentException();
                uri = manager.resolve(uri);
            }
            validateSyntax(uri);
            Entry owner = owner(uri);
            if (owner == null || reserved(uri, owner.base())) throw new IllegalArgumentException();
            return uri.toASCIIString();
        } catch (RuntimeException invalid) {
            throw new BusinessException("AUTH_400_REDIRECT", HttpStatus.BAD_REQUEST, "不允许的登录返回地址");
        }
    }

    public String destination(String validatedReturnUrl, String defaultSystem, Set<String> permissions) {
        if (validatedReturnUrl != null) {
            String target = validateReturnUrl(validatedReturnUrl);
            if (authorizedSystemsUrl().equals(target)) return target;
            Entry owner = owner(URI.create(target));
            return permissions.contains(owner.type()) ? target : authorizedSystemsUrl();
        }
        return entries.stream().filter(e -> e.type().equals(defaultSystem) && permissions.contains(e.type()))
            .map(Entry::home).findFirst().orElseGet(this::authorizedSystemsUrl);
    }

    private Entry owner(URI uri) {
        return entries.stream().filter(e -> contains(e.base(), uri)).findFirst().orElse(null);
    }

    private static boolean sameHostAndScheme(URI a, URI b) {
        return a.getScheme().equalsIgnoreCase(b.getScheme()) && a.getHost().equalsIgnoreCase(b.getHost());
    }

    private static int port(URI uri) {
        return uri.getPort() == -1 ? (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80) : uri.getPort();
    }

    private static boolean contains(URI base, URI target) {
        return sameHostAndScheme(base, target) && port(base) == port(target)
            && target.getPath().startsWith(base.getPath());
    }

    private static void validateSyntax(URI uri) {
        if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))
            || uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() > 65535
            || uri.toString().matches("(?is).*(%0[0-9a-f]|%1[0-9a-f]|%7f|%5c|[\\\\\\p{Cntrl}]).*")
            || uri.getRawPath().matches("(?is).*(%2f|%25).*")) {
            throw new IllegalArgumentException("Invalid browser URL");
        }
        String path = uri.getPath();
        if (path.contains("//") || List.of(path.split("/", -1)).stream().anyMatch(p -> p.equals(".") || p.equals(".."))) {
            throw new IllegalArgumentException("Ambiguous browser path");
        }
    }

    private static boolean reserved(URI uri, URI base) {
        String relative = "/" + uri.getPath().substring(base.getPath().length());
        String fragment = uri.getFragment();
        return reservedPath(relative) || (fragment != null && fragment.startsWith("/") && reservedPath(fragment.split("\\?", 2)[0]));
    }

    private static boolean reservedPath(String path) {
        return List.of("/login", "/api", "/oauth2", "/auth-bridge", "/logout", "/csrf").stream()
            .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
