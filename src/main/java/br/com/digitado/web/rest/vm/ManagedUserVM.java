package br.com.digitado.web.rest.vm;

import br.com.digitado.security.PasswordPolicy;
import br.com.digitado.service.dto.AdminUserDTO;
import jakarta.validation.constraints.Size;

/**
 * View Model extending the AdminUserDTO, which is meant to be used in the user management UI.
 */
public class ManagedUserVM extends AdminUserDTO {

    // Fonte única dos limites: PasswordPolicy (mesma regra do registro/troca/reset)
    public static final int PASSWORD_MIN_LENGTH = PasswordPolicy.MIN_LENGTH;

    public static final int PASSWORD_MAX_LENGTH = PasswordPolicy.MAX_LENGTH;

    @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH)
    private String password;

    public ManagedUserVM() {
        // Empty constructor needed for Jackson.
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ManagedUserVM{" + super.toString() + "} ";
    }
}
