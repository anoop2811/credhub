package io.pivotal.security.repository

import io.pivotal.security.entity.NamedSecret
import io.pivotal.security.model.Secret
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
open class InMemorySecretStore
@Autowired
constructor(private val secretRepository: InMemorySecretRepository) : SecretStore {

  @Transactional
  override fun set(key: String, secret: Secret) {
    val namedSecret: NamedSecret = secretRepository.findOneByName(key) ?: NamedSecret(name = key)
    namedSecret.type = secret.type!!
    namedSecret.value = secret.value
    secretRepository.save<NamedSecret>(namedSecret)
  }

  override fun get(key: String): Secret? {
    val namedSecret = secretRepository.findOneByName(key)
    return namedSecret?.let { s ->
      return Secret(value = s.value, type = s.type)
    }
  }

  @Transactional
  override fun delete(key: String): Secret? {
    val namedSecret = secretRepository.findOneByName(key)
    return namedSecret?.let { s ->
      secretRepository.delete(s)
      return Secret(value = s.value, type = s.type)
    }
  }

}