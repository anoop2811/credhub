package io.pivotal.security.domain;

import io.pivotal.security.credential.UserCredentialValue;
import io.pivotal.security.entity.UserCredentialData;
import io.pivotal.security.request.StringGenerationParameters;
import io.pivotal.security.service.Encryption;
import io.pivotal.security.util.JsonObjectMapper;

import java.io.IOException;

public class UserCredential extends Credential<UserCredential> {
  private final UserCredentialData delegate;
  private StringGenerationParameters generationParameters;
  private JsonObjectMapper jsonObjectMapper;

  public UserCredential() {
    this(new UserCredentialData());
  }

  public UserCredential(UserCredentialData delegate) {
    super(delegate);
    this.delegate = delegate;
    jsonObjectMapper = new JsonObjectMapper();
  }

  public UserCredential(String name) {
    this(new UserCredentialData(name));
  }

  public UserCredential(
      UserCredentialValue userValue,
      StringGenerationParameters generationParameters,
      Encryptor encryptor
  ) {
    this();
    this.setEncryptor(encryptor);
    this.setPassword(userValue.getPassword());
    this.setUsername(userValue.getUsername());
    this.setGenerationParameters(generationParameters);
    this.setSalt(userValue.getSalt());
  }

  @Override
  public String getCredentialType() {
    return delegate.getCredentialType();
  }

  @Override
  public void rotate() {
    String decryptedPassword = getPassword();
    StringGenerationParameters decryptedGenerationParameters = getGenerationParameters();

    setPassword(decryptedPassword);
    setGenerationParameters(decryptedGenerationParameters);
  }

  public UserCredential setPassword(String password) {
    Encryption passwordEncryption = encryptor.encrypt(password);
    delegate.setEncryptionKeyUuid(passwordEncryption.canaryUuid);
    delegate.setEncryptedValue(passwordEncryption.encryptedValue);
    delegate.setNonce(passwordEncryption.nonce);
    return this;
  }

  public String getPassword() {
    return encryptor.decrypt(new Encryption(
        delegate.getEncryptionKeyUuid(),
        delegate.getEncryptedValue(),
        delegate.getNonce()));
  }

  public UserCredential setUsername(String username) {
    delegate.setUsername(username);
    return this;
  }

  public String getUsername() {
    return delegate.getUsername();
  }

  public String getSalt() {
    return delegate.getSalt();
  }

  public UserCredential setSalt(String salt) {
    delegate.setSalt(salt);
    return this;
  }

  public UserCredential setGenerationParameters(StringGenerationParameters generationParameters) {
    try {
      String generationParameterJson =
          generationParameters != null ? jsonObjectMapper.writeValueAsString(generationParameters)
              : null;

      Encryption encryptedParameters = encryptor.encrypt(generationParameterJson);
      delegate.setEncryptionKeyUuid(encryptedParameters.canaryUuid);
      delegate.setEncryptedGenerationParameters(encryptedParameters.encryptedValue);
      delegate.setParametersNonce(encryptedParameters.nonce);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  public StringGenerationParameters getGenerationParameters() {
    String parameterJson = encryptor.decrypt(new Encryption(
        delegate.getEncryptionKeyUuid(),
        delegate.getEncryptedGenerationParameters(),
        delegate.getParametersNonce())
    );

    if (parameterJson == null) {
      return null;
    }

    try {
      StringGenerationParameters generationParameters = jsonObjectMapper
          .deserializeBackwardsCompatibleValue(parameterJson, StringGenerationParameters.class);
      return generationParameters;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
