package szs.baosys.manager.api;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import szs.baosys.common.api.ApiResponse;
import szs.baosys.common.api.PageResponse;
import szs.baosys.manager.api.dto.CurrentUserResponse;
import szs.baosys.manager.api.dto.LoginClientResponse;
import szs.baosys.manager.api.dto.UserCreateRequest;
import szs.baosys.manager.api.dto.UserResponse;
import szs.baosys.manager.api.dto.UserUpdateRequest;
import szs.baosys.manager.api.enums.SystemType;

public interface UserApi {

    String SERVICE_NAME = "baosys-manager";
    String PREFIX = "/v1/users";

    @GetMapping("/me")
    ApiResponse<CurrentUserResponse> currentUser(Principal principal);

    @GetMapping
    ApiResponse<PageResponse<UserResponse>> list(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "systemType", required = false) SystemType systemType,
            @Min(0) @RequestParam(name = "page", defaultValue = "0") int page,
            @Min(1) @Max(200) @RequestParam(name = "size", defaultValue = "20") int size);

    @PostMapping
    ApiResponse<UserResponse> create(@Valid @RequestBody UserCreateRequest request);

    @PutMapping
    ApiResponse<UserResponse> update(@Valid @RequestBody UserUpdateRequest request);

    @DeleteMapping("/{id}")
    ApiResponse<Void> delete(@PathVariable("id") long id);

    @GetMapping("/{id}/login-clients")
    ApiResponse<List<LoginClientResponse>> loginClients(@PathVariable("id") long id);

    @DeleteMapping("/{id}/login-clients/{loginId}")
    ApiResponse<Void> kickClient(
            @PathVariable("id") long id,
            @PathVariable("loginId") long loginId);

    @DeleteMapping("/{id}/login-clients")
    ApiResponse<Void> kickAllClients(@PathVariable("id") long id);
}
