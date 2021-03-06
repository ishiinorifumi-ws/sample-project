package jp.co.disney.spplogin.web.form;

import javax.validation.constraints.Size;

import org.hibernate.validator.constraints.NotBlank;

import lombok.Data;

@Data
public class LoginForm {
	@NotBlank
	private String password;
	
	@NotBlank
	@Size(max=255)
	private String memberNameOrEmailAddr;
}
