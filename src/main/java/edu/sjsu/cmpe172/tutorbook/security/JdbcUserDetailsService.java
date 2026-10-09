package edu.sjsu.cmpe172.tutorbook.security;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import edu.sjsu.cmpe172.tutorbook.repository.UserRepository;

/**
 * Called by Spring Security during login. It reads the user's row (including
 * the BCrypt password hash and the role) over JDBC; Spring Security then
 * checks the typed password against the hash with BCryptPasswordEncoder.
 */
@Service
public class JdbcUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public JdbcUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AppUser loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user " + email));
    }
}
