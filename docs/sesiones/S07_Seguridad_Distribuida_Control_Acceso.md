# S07 - Seguridad Distribuida y Control de Acceso

## 1. Propósito
Implementar seguridad perimetral y distribuida (Defensa en profundidad) utilizando **OAuth2 / JWT** y control de acceso basado en roles (**RBAC**).

## 2. Principio de Defensa en Profundidad
No basta con proteger las rutas en el Gateway. Cada microservicio de negocio (`oorden-ms`, `cliente-ms`, `facturacion-ms`, `inventario-ms`) actúa como **Resource Server** y valida de forma autónoma el token Bearer en su filtro de Spring Security:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/oóordenes/**").hasAnyRole("CLIENTE", "FARMACEUTICO", "ADMIN")
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
}
```

