package baosys.manager.app.controllers;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import baosys.common.security.jwt.SigningKeyMaterial;

/** Standard public JWKS, deliberately without the ApiResponse envelope. */
@RestController
public class JwksController {
    private final SigningKeyMaterial material;
    public JwksController(SigningKeyMaterial material) { this.material = material; }
    @GetMapping(value = "/oauth2/jwks", produces = "application/json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(material.publicJwks());
    }
}
