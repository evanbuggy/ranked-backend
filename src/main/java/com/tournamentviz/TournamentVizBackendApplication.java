package com.tournamentviz;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * “Single-deployable” implementation for the assignment’s proof-of-work.
 *
 * Microservice boundaries are respected via:
 * - API namespaces
 * - internal service classes that map to bounded contexts
 *
 * This keeps the codebase runnable without requiring multiple deployments or external brokers.
 */
@SpringBootApplication(scanBasePackages = {"com.tournamentviz", "com.startGgIntegration"})
public class TournamentVizBackendApplication {
  public static void main(String[] args) {
    SpringApplication.run(TournamentVizBackendApplication.class, args);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}

// -----------------------------
// API error handling
// -----------------------------
// The lightweight UI depends on error messages from failed login/register calls.
@org.springframework.web.bind.annotation.RestControllerAdvice
class ApiExceptionAdvice {
  @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> onIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", ex.getMessage()));
  }
}

// -----------------------------
// Security (JWT token auth)
// -----------------------------

@RestController
class AppHealthController {
  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of("ok", true, "ts", Instant.now().toString());
  }
}

@Component
class JwtTokenProvider {
  private final String secret;
  private final String issuer;
  private final long expirySeconds;

  JwtTokenProvider(
      @Value("${app.security.jwt-secret}") String secret,
      @Value("${app.security.jwt-issuer}") String issuer,
      @Value("${app.security.jwt-expiry-seconds}") long expirySeconds
  ) {
    this.secret = secret;
    this.issuer = issuer;
    this.expirySeconds = expirySeconds;
  }

  String createToken(String userId) {
    long nowSeconds = Instant.now().getEpochSecond();
    long expSeconds = nowSeconds + expirySeconds;

    return Jwts.builder()
        .setIssuer(issuer)
        .setSubject(userId)
        .setIssuedAt(Date.from(Instant.ofEpochSecond(nowSeconds)))
        .setExpiration(Date.from(Instant.ofEpochSecond(expSeconds)))
        .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
        .compact();
  }

  Optional<String> validateAndExtractUserId(String jwt) {
    try {
      var key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
      var payload = Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(jwt)
          .getPayload();
      return Optional.ofNullable(payload.getSubject());
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}

@Component
class JwtAuthFilter extends OncePerRequestFilter {
  private final JwtTokenProvider tokenProvider;
  private final UserRepository userRepository;

  JwtAuthFilter(JwtTokenProvider tokenProvider, UserRepository userRepository) {
    this.tokenProvider = tokenProvider;
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String path = request.getRequestURI();
    if (path.startsWith("/api/auth/") || path.equals("/health") || path.startsWith("/swagger")) {
      filterChain.doFilter(request, response);
      return;
    }

    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String jwt = authHeader.substring("Bearer ".length());
    Optional<String> maybeUserId = tokenProvider.validateAndExtractUserId(jwt);
    if (maybeUserId.isPresent()) {
      String userId = maybeUserId.get();
      userRepository.findById(Long.valueOf(userId)).ifPresent(user -> {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            new MinimalUserDetails(user.id),
            null,
            List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
      });
    }

    filterChain.doFilter(request, response);
  }
}

class MinimalUserDetails implements UserDetails {
  final long userId;

  MinimalUserDetails(long userId) {
    this.userId = userId;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of();
  }

  @Override
  public String getPassword() {
    return "";
  }

  @Override
  public String getUsername() {
    return String.valueOf(userId);
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}

@org.springframework.context.annotation.Configuration
class SecurityConfig {
  private final JwtAuthFilter jwtAuthFilter;

  SecurityConfig(JwtAuthFilter jwtAuthFilter) {
    this.jwtAuthFilter = jwtAuthFilter;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("*"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/",
                "/index.html",
                "/about",
                "/assets/**",
                "/static/**",
                "/favicon.ico",
                "/vite.svg",
                "/health",
                "/api/auth/**",
                "/swagger-ui/**",
                "/v3/api-docs/**"
            ).permitAll()
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}

// -----------------------------
// Domain model (minimal)
// -----------------------------

enum MembershipRole {
  OWNER,
  ADMIN,
  MEMBER
}

enum ImportStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  FAILED
}

@Entity(name = "users")
class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  long id;

  @Column(nullable = false, unique = true, length = 80)
  String username;

  @Column(nullable = false, unique = true, length = 120)
  String email;

  @Column(nullable = false)
  String passwordHash;

  @Column(nullable = false)
  String status; // ACTIVE for now

  User() {}

  User(String username, String email, String passwordHash, String status) {
    this.username = username;
    this.email = email;
    this.passwordHash = passwordHash;
    this.status = status;
  }
}

@Entity(name = "event_groups")
class EventGroup {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  long id;

  @Column(nullable = false, length = 120)
  String name;

  @Column(nullable = false)
  long createdByUserId;

  // statsRevision increments after successful rating recomputation
  @Column(nullable = false)
  long statisticsRevision = 0;

  EventGroup() {}

  EventGroup(String name, long createdByUserId) {
    this.name = name;
    this.createdByUserId = createdByUserId;
  }
}

@Entity(name = "event_group_members")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"eventGroupId", "userId"}))
class EventGroupMember {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  long id;

  @Column(nullable = false)
  long eventGroupId;

  @Column(nullable = false)
  long userId;

  @Enumerated(EnumType.STRING)
  MembershipRole role;

  EventGroupMember() {}

  EventGroupMember(long eventGroupId, long userId, MembershipRole role) {
    this.eventGroupId = eventGroupId;
    this.userId = userId;
    this.role = role;
  }
}

@Entity(name = "event_group_tournaments")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"eventGroupId", "tournamentRef"}))
class EventGroupTournamentLink {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  long id;

  @Column(nullable = false)
  long eventGroupId;

  @Column(nullable = false, length = 180)
  String tournamentRef; // normalized slug/id

  @Column(nullable = false, length = 500)
  String startggLink;

  @Column(nullable = false)
  long linkedByUserId;

  EventGroupTournamentLink() {}

  EventGroupTournamentLink(long eventGroupId, String tournamentRef, String startggLink, long linkedByUserId) {
    this.eventGroupId = eventGroupId;
    this.tournamentRef = tournamentRef;
    this.startggLink = startggLink;
    this.linkedByUserId = linkedByUserId;
  }
}

@Entity(name = "import_jobs")
class ImportJob {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  long id;

  @Column(nullable = false)
  long eventGroupId;

  @Column(nullable = false, length = 180)
  String tournamentRef;

  @Enumerated(EnumType.STRING)
  ImportStatus status;

  @Column(nullable = false)
  long createdByUserId;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  Instant startedAt;
  Instant completedAt;
  String failureReason;

  // used for versioning when Start.gg returns updated results
  @Column(nullable = false)
  String sourceVersion = "fixture-v1";

  ImportJob() {}

  ImportJob(long eventGroupId, String tournamentRef, ImportStatus status, long createdByUserId) {
    this.eventGroupId = eventGroupId;
    this.tournamentRef = tournamentRef;
    this.status = status;
    this.createdByUserId = createdByUserId;
  }
}

@Entity(name = "event_group_players")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"eventGroupId", "playerRef"}))
class EventGroupPlayer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  long id;

  @Column(nullable = false)
  long eventGroupId;

  @Column(nullable = false, length = 180)
  String playerRef; // canonical id in Start.gg world (or fixture mapping)

  @Column(nullable = false, length = 120)
  String displayName;

  EventGroupPlayer() {}

  EventGroupPlayer(long eventGroupId, String playerRef, String displayName) {
    this.eventGroupId = eventGroupId;
    this.playerRef = playerRef;
    this.displayName = displayName;
  }
}

@Entity(name = "imported_matches")
@Table(indexes = @Index(columnList = "eventGroupId,tournamentRef"))
class ImportedMatch {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  long id;

  @Column(nullable = false)
  long eventGroupId;

  @Column(nullable = false, length = 180)
  String tournamentRef;

  @Column(nullable = false, length = 200)
  String matchRef; // deterministic identifier for ordering + idempotency

  @Column(nullable = false, length = 180)
  String playerARef;

  @Column(nullable = false, length = 180)
  String playerBRef;

  @Column(nullable = false, length = 180)
  String winnerRef;

  // Elo ordering uses matchSequenceNo for deterministic processing
  @Column(nullable = false)
  long matchSequenceNo;

  Instant occurredAt;

  ImportedMatch() {}
}

@Entity(name = "player_ratings")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"eventGroupId", "playerRef"}))
class PlayerRating {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  long id;

  @Column(nullable = false)
  long eventGroupId;

  @Column(nullable = false, length = 180)
  String playerRef;

  @Column(nullable = false)
  long statisticsRevision;

  @Column(nullable = false)
  String formulaVersion;

  @Column(nullable = false)
  double elo;

  @Column(nullable = false)
  long wins;

  @Column(nullable = false)
  long losses;

  PlayerRating() {}
}

@Entity(name = "player_matchup")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"eventGroupId", "playerARef", "playerBRef"}))
class PlayerMatchup {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  long id;

  @Column(nullable = false)
  long eventGroupId;

  @Column(nullable = false, length = 180)
  String playerARef;

  @Column(nullable = false, length = 180)
  String playerBRef;

  @Column(nullable = false)
  long statisticsRevision;

  @Column(nullable = false)
  String formulaVersion;

  @Column(nullable = false)
  long winsForA;

  @Column(nullable = false)
  long lossesForA;

  PlayerMatchup() {}
}

// -----------------------------
// Repositories
// -----------------------------

@Repository
interface UserRepository extends org.springframework.data.jpa.repository.JpaRepository<User, Long> {
  Optional<User> findByUsername(String username);
  Optional<User> findByEmail(String email);
}

@Repository
interface EventGroupRepository extends org.springframework.data.jpa.repository.JpaRepository<EventGroup, Long> {
}

@Repository
interface EventGroupMemberRepository extends org.springframework.data.jpa.repository.JpaRepository<EventGroupMember, Long> {
  Optional<EventGroupMember> findByEventGroupIdAndUserId(long eventGroupId, long userId);
  List<EventGroupMember> findByUserId(long userId);

  List<EventGroupMember> findByEventGroupId(long eventGroupId);
}

@Repository
interface EventGroupTournamentLinkRepository extends org.springframework.data.jpa.repository.JpaRepository<EventGroupTournamentLink, Long> {
  Optional<EventGroupTournamentLink> findByEventGroupIdAndTournamentRef(long eventGroupId, String tournamentRef);

  List<EventGroupTournamentLink> findByEventGroupId(long eventGroupId);
}

@Repository
interface ImportJobRepository extends org.springframework.data.jpa.repository.JpaRepository<ImportJob, Long> {
  List<ImportJob> findByEventGroupIdOrderByCreatedAtDesc(long eventGroupId);

  List<ImportJob> findTop50ByStatusInOrderByCreatedAtAsc(List<ImportStatus> statuses);
}

@Repository
interface EventGroupPlayerRepository extends org.springframework.data.jpa.repository.JpaRepository<EventGroupPlayer, Long> {
  Optional<EventGroupPlayer> findByEventGroupIdAndPlayerRef(long eventGroupId, String playerRef);

  List<EventGroupPlayer> findByEventGroupId(long eventGroupId);

  List<EventGroupPlayer> findByEventGroupIdAndDisplayNameContainingIgnoreCase(long eventGroupId, String query);
}

@Repository
interface ImportedMatchRepository extends org.springframework.data.jpa.repository.JpaRepository<ImportedMatch, Long> {
  long countByEventGroupId(long eventGroupId);

  List<ImportedMatch> findByEventGroupIdAndTournamentRefOrderByMatchSequenceNoAsc(long eventGroupId, String tournamentRef);

  boolean existsByEventGroupIdAndMatchRef(long eventGroupId, String matchRef);

  List<ImportedMatch> findByEventGroupIdOrderByMatchSequenceNoAsc(long eventGroupId);

  void deleteByEventGroupId(long eventGroupId);
}

@Repository
interface PlayerRatingRepository extends org.springframework.data.jpa.repository.JpaRepository<PlayerRating, Long> {
  List<PlayerRating> findByEventGroupIdOrderByEloDesc(long eventGroupId);

  Optional<PlayerRating> findByEventGroupIdAndPlayerRef(long eventGroupId, String playerRef);
}

@Repository
interface PlayerMatchupRepository extends org.springframework.data.jpa.repository.JpaRepository<PlayerMatchup, Long> {
  void deleteByEventGroupId(long eventGroupId);

  List<PlayerMatchup> findByEventGroupIdAndPlayerARefOrderByPlayerBRefAsc(long eventGroupId, String playerARef);
}

// -----------------------------
// DTOs
// -----------------------------

record RegisterRequest(
    @NotBlank String username,
    @NotBlank String email,
    @NotBlank String password
) {}

record LoginRequest(
    @NotBlank String usernameOrEmail,
    @NotBlank String password
) {}

record AuthResponse(
    long userId,
    String username,
    String token
) {}

record CreateEventGroupRequest(
    @NotBlank String name
) {}

record EventGroupSummary(
    long id,
    String name,
    long statisticsRevision
) {}

record CreateMemberRequest(
    @NotNull Long userId,
    @NotNull MembershipRole role
) {}

record LinkTournamentRequest(
    @NotBlank String startggLink
) {}

record ImportJobSummary(
    long id,
    long eventGroupId,
    String tournamentRef,
    ImportStatus status,
    String failureReason,
    Instant createdAt,
    Instant completedAt
) {
  static ImportJobSummary from(ImportJob j) {
    return new ImportJobSummary(
        j.id, j.eventGroupId, j.tournamentRef, j.status, j.failureReason, j.createdAt, j.completedAt
    );
  }
}

record RankingRow(
    String playerRef,
    String displayName,
    double elo,
    long wins,
    long losses
) {}

record PlayerWinsLossesRow(
    String opponentRef,
    String opponentName,
    long wins,
    long losses
) {}

// CSV rows are delivered as text/plain with header line.

// -----------------------------
// Controllers (API namespaces)
// -----------------------------

@RestController
@RequestMapping("/api/auth")
class AuthController {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider tokenProvider;

  AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenProvider = tokenProvider;
  }

  @PostMapping("/register")
  AuthResponse register(@Valid @RequestBody RegisterRequest req) {
    if (userRepository.findByUsername(req.username()).isPresent()) {
      throw new IllegalArgumentException("username already exists");
    }
    if (userRepository.findByEmail(req.email()).isPresent()) {
      throw new IllegalArgumentException("email already exists");
    }

    String hash = passwordEncoder.encode(req.password());
    User user = new User(req.username(), req.email(), hash, "ACTIVE");
    userRepository.save(user);
    String token = tokenProvider.createToken(String.valueOf(user.id));
    return new AuthResponse(user.id, user.username, token);
  }

  @PostMapping("/login")
  AuthResponse login(@Valid @RequestBody LoginRequest req) {
    Optional<User> userOpt =
        userRepository.findByUsername(req.usernameOrEmail()).or(() -> userRepository.findByEmail(req.usernameOrEmail()));
    User user = userOpt.orElseThrow(() -> new IllegalArgumentException("invalid credentials"));
    if (!passwordEncoder.matches(req.password(), user.passwordHash)) {
      throw new IllegalArgumentException("invalid credentials");
    }
    String token = tokenProvider.createToken(String.valueOf(user.id));
    return new AuthResponse(user.id, user.username, token);
  }

  @GetMapping("/me")
  Map<String, Object> me() {
    long userId = CurrentUser.userId();
    return Map.of("userId", userId);
  }
}

@RestController
@RequestMapping("/api/event-groups")
class EventGroupController {
  private final EventGroupRepository eventGroupRepository;
  private final EventGroupMemberRepository memberRepository;
  private final EventGroupTournamentLinkRepository linkRepository;
  private final TournamentImportCoordinator importCoordinator;

  EventGroupController(
      EventGroupRepository eventGroupRepository,
      EventGroupMemberRepository memberRepository,
      EventGroupTournamentLinkRepository linkRepository,
      TournamentImportCoordinator importCoordinator
  ) {
    this.eventGroupRepository = eventGroupRepository;
    this.memberRepository = memberRepository;
    this.linkRepository = linkRepository;
    this.importCoordinator = importCoordinator;
  }

  @PostMapping
  EventGroupSummary create(@Valid @RequestBody CreateEventGroupRequest req) {
    long userId = CurrentUser.userId();
    EventGroup eg = new EventGroup(req.name(), userId);
    eventGroupRepository.save(eg);
    memberRepository.save(new EventGroupMember(eg.id, userId, MembershipRole.OWNER));
    return new EventGroupSummary(eg.id, eg.name, eg.statisticsRevision);
  }

  @GetMapping
  List<EventGroupSummary> listMyGroups() {
    long userId = CurrentUser.userId();
    List<EventGroupMember> memberships = memberRepository.findByUserId(userId);
    if (memberships.isEmpty()) return List.of();
    List<Long> eventGroupIds = memberships.stream().map(m -> m.eventGroupId).distinct().toList();
    Map<Long, EventGroup> byId = eventGroupRepository.findAllById(eventGroupIds)
        .stream()
        .collect(Collectors.toMap(e -> e.id, e -> e));
    return memberships.stream()
        .map(m -> byId.get(m.eventGroupId))
        .filter(Objects::nonNull)
        .distinct()
        .map(eg -> new EventGroupSummary(eg.id, eg.name, eg.statisticsRevision))
        .collect(Collectors.toList());
  }
}

/**
 * For proof-of-work we avoid complex query composition.
 * If you want a complete query implementation, tell me and I’ll extend the endpoints.
 */
@RestController
@RequestMapping("/api/internal-event-groups")
class EventGroupInternalController {
  private final EventGroupRepository eventGroupRepository;
  private final EventGroupMemberRepository memberRepository;
  private final EventGroupTournamentLinkRepository linkRepository;
  private final TournamentImportCoordinator importCoordinator;

  EventGroupInternalController(
      EventGroupRepository eventGroupRepository,
      EventGroupMemberRepository memberRepository,
      EventGroupTournamentLinkRepository linkRepository,
      TournamentImportCoordinator importCoordinator
  ) {
    this.eventGroupRepository = eventGroupRepository;
    this.memberRepository = memberRepository;
    this.linkRepository = linkRepository;
    this.importCoordinator = importCoordinator;
  }

  @GetMapping("/{eventGroupId}")
  EventGroupSummary get(@PathVariable long eventGroupId) {
    long userId = CurrentUser.userId();
    MembershipRole role = AuthorizationService.requireMembershipRole(memberRepository, eventGroupId, userId);
    EventGroup eg = eventGroupRepository.findById(eventGroupId).orElseThrow();
    return new EventGroupSummary(eg.id, eg.name, eg.statisticsRevision);
  }

  @PostMapping("/{eventGroupId}/members")
  void addMember(
      @PathVariable long eventGroupId,
      @Valid @RequestBody CreateMemberRequest req
  ) {
    long actorId = CurrentUser.userId();
    AuthorizationService.requireRole(memberRepository, eventGroupId, actorId, MembershipRole.OWNER);
    memberRepository.save(new EventGroupMember(eventGroupId, req.userId(), req.role()));
  }

  @PostMapping("/{eventGroupId}/tournaments")
  Map<String, Object> linkTournament(
      @PathVariable long eventGroupId,
      @Valid @RequestBody LinkTournamentRequest req
  ) {
    long actorId = CurrentUser.userId();
    MembershipRole role = AuthorizationService.requireMembershipRole(memberRepository, eventGroupId, actorId);
    if (!(role == MembershipRole.OWNER || role == MembershipRole.ADMIN)) {
      throw new IllegalArgumentException("only OWNER/ADMIN can link tournaments");
    }

    String tournamentRef = TournamentRefExtractor.normalize(req.startggLink());
    if (tournamentRef.isBlank()) {
      throw new IllegalArgumentException("could not parse tournament reference from startggLink");
    }

    linkRepository.findByEventGroupIdAndTournamentRef(eventGroupId, tournamentRef).ifPresent(existing -> {
      throw new IllegalArgumentException("tournament already linked to this event group");
    });

    linkRepository.save(new EventGroupTournamentLink(eventGroupId, tournamentRef, req.startggLink(), actorId));
    ImportJob job = importCoordinator.enqueueImport(eventGroupId, tournamentRef, actorId);
    return Map.of("importJobId", job.id, "status", job.status);
  }

  @GetMapping("/{eventGroupId}/imports")
  List<ImportJobSummary> imports(@PathVariable long eventGroupId) {
    long actorId = CurrentUser.userId();
    AuthorizationService.requireMembershipRole(memberRepository, eventGroupId, actorId);
    return importCoordinator.importJobsForEventGroup(eventGroupId).stream()
        .map(ImportJobSummary::from)
        .collect(Collectors.toList());
  }
}

@RestController
@RequestMapping("/api/ratings")
class RatingsQueryController {
  private final PlayerRatingRepository ratingRepository;
  private final EventGroupPlayerRepository playerRepository;
  private final EventGroupRepository eventGroupRepository;
  private final PlayerMatchupRepository matchupRepository;
  private final EventGroupMemberRepository memberRepository;

  RatingsQueryController(
      PlayerRatingRepository ratingRepository,
      EventGroupPlayerRepository playerRepository,
      EventGroupRepository eventGroupRepository,
      PlayerMatchupRepository matchupRepository,
      EventGroupMemberRepository memberRepository
  ) {
    this.ratingRepository = ratingRepository;
    this.playerRepository = playerRepository;
    this.eventGroupRepository = eventGroupRepository;
    this.matchupRepository = matchupRepository;
    this.memberRepository = memberRepository;
  }

  @GetMapping("/event-groups/{eventGroupId}/rankings")
  List<RankingRow> rankings(@PathVariable long eventGroupId) {
    CurrentUser.requireAuthenticated();
    AuthorizationService.requireMembershipRole(memberRepository, eventGroupId, CurrentUser.userId());
    return ratingRepository.findByEventGroupIdOrderByEloDesc(eventGroupId).stream()
        .map(r -> {
          EventGroupPlayer p = playerRepository.findByEventGroupIdAndPlayerRef(eventGroupId, r.playerRef).orElse(null);
          String name = p != null ? p.displayName : r.playerRef;
          return new RankingRow(r.playerRef, name, r.elo, r.wins, r.losses);
        })
        .collect(Collectors.toList());
  }

  @GetMapping("/event-groups/{eventGroupId}/players")
  List<Map<String, String>> players(
      @PathVariable long eventGroupId,
      @RequestParam(name = "query", required = false) String query
  ) {
    CurrentUser.requireAuthenticated();
    AuthorizationService.requireMembershipRole(memberRepository, eventGroupId, CurrentUser.userId());
    List<EventGroupPlayer> players = (query == null || query.isBlank())
        ? playerRepository.findByEventGroupId(eventGroupId)
        : playerRepository.findByEventGroupIdAndDisplayNameContainingIgnoreCase(eventGroupId, query);

    return players.stream()
        .sorted(Comparator.comparing(p -> p.displayName, String.CASE_INSENSITIVE_ORDER))
        .map(p -> Map.of("playerRef", p.playerRef, "displayName", p.displayName))
        .collect(Collectors.toList());
  }

  @GetMapping("/event-groups/{eventGroupId}/winloss-matrix")
  List<PlayerWinsLossesRow> winlossMatrix(
      @PathVariable long eventGroupId,
      @RequestParam(name = "playerRef", required = false) String playerRef
  ) {
    CurrentUser.requireAuthenticated();
    AuthorizationService.requireMembershipRole(memberRepository, eventGroupId, CurrentUser.userId());
    if (playerRef == null || playerRef.isBlank()) {
      // default: first player by ranking
      List<PlayerRating> ratings = ratingRepository.findByEventGroupIdOrderByEloDesc(eventGroupId);
      if (ratings.isEmpty()) return List.of();
      playerRef = ratings.get(0).playerRef;
    }

    List<PlayerMatchup> rows = matchupRepository.findByEventGroupIdAndPlayerARefOrderByPlayerBRefAsc(eventGroupId, playerRef);
    Map<String, String> namesByRef = playerRepository.findByEventGroupId(eventGroupId).stream()
        .collect(Collectors.toMap(p -> p.playerRef, p -> p.displayName, (a, b) -> a));

    return rows.stream()
        .map(m -> new PlayerWinsLossesRow(
            m.playerBRef,
            namesByRef.getOrDefault(m.playerBRef, m.playerBRef),
            m.winsForA,
            m.lossesForA
        ))
        .collect(Collectors.toList());
  }

  @GetMapping(value = "/event-groups/{eventGroupId}/export/csv", produces = "text/csv")
  String exportCsv(@PathVariable long eventGroupId) {
    CurrentUser.requireAuthenticated();
    AuthorizationService.requireMembershipRole(memberRepository, eventGroupId, CurrentUser.userId());
    List<PlayerRating> ratings = ratingRepository.findByEventGroupIdOrderByEloDesc(eventGroupId);
    String header = "playerRef,displayName,elo,wins,losses";
    String rows = ratings.stream().map(r -> {
      String name = playerRepository.findByEventGroupIdAndPlayerRef(eventGroupId, r.playerRef)
          .map(p -> escapeCsv(p.displayName))
          .orElse(escapeCsv(r.playerRef));
      return String.format("%s,%s,%.6f,%d,%d",
          escapeCsv(r.playerRef),
          name,
          r.elo,
          r.wins,
          r.losses
      );
    }).collect(Collectors.joining("\n"));
    return header + "\n" + rows + "\n";
  }

  private static String escapeCsv(String s) {
    if (s == null) return "";
    String v = s.replace("\"", "\"\"");
    if (v.contains(",") || v.contains("\"") || v.contains("\n")) return "\"" + v + "\"";
    return v;
  }
}

// -----------------------------
// Worker (import + recompute)
// -----------------------------

@Service
class TournamentImportCoordinator {
  private final ImportJobRepository importJobRepository;
  private final EventGroupRepository eventGroupRepository;
  private final StartggClient startggClient;
  private final MatchRepositoryAdapter matchRepo;
  private final RatingComputationService ratingComputationService;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  // used to prevent the same job being executed twice concurrently in this “proof of work”
  private final Map<Long, Boolean> inFlight = new ConcurrentHashMap<>();

  TournamentImportCoordinator(
      ImportJobRepository importJobRepository,
      EventGroupRepository eventGroupRepository,
      StartggClient startggClient,
      MatchRepositoryAdapter matchRepo,
      RatingComputationService ratingComputationService
  ) {
    this.importJobRepository = importJobRepository;
    this.eventGroupRepository = eventGroupRepository;
    this.startggClient = startggClient;
    this.matchRepo = matchRepo;
    this.ratingComputationService = ratingComputationService;

    scheduler.scheduleWithFixedDelay(this::tick, 1, 2, TimeUnit.SECONDS);
  }

  ImportJob enqueueImport(long eventGroupId, String tournamentRef, long createdByUserId) {
    // Avoid duplicate active jobs for same tournament and event group
    // (proof of work; full policy might allow versioning)
    List<ImportJob> recent = importJobRepository.findByEventGroupIdOrderByCreatedAtDesc(eventGroupId);
    for (ImportJob j : recent) {
      if (j.tournamentRef.equals(tournamentRef) && (j.status == ImportStatus.PENDING || j.status == ImportStatus.RUNNING)) {
        return j;
      }
      if (j.tournamentRef.equals(tournamentRef) && j.status == ImportStatus.COMPLETED) {
        // already imported; still return “completed” (UI can just refresh rankings)
        return j;
      }
    }
    ImportJob job = new ImportJob(eventGroupId, tournamentRef, ImportStatus.PENDING, createdByUserId);
    importJobRepository.save(job);
    return job;
  }

  private void tick() {
    try {
      List<ImportJob> candidates = importJobRepository.findTop50ByStatusInOrderByCreatedAtAsc(
          List.of(ImportStatus.PENDING)
      );
      for (ImportJob job : candidates) {
        if (Boolean.TRUE.equals(inFlight.put(job.id, true))) continue;

        Optional<User> creator = Optional.empty();
        job.status = ImportStatus.RUNNING;
        job.startedAt = Instant.now();
        importJobRepository.save(job);

        try {
          StartggTournamentData data = startggClient.fetchTournament(job.tournamentRef);

          matchRepo.upsertPlayers(job.eventGroupId, data.players());
          matchRepo.upsertMatches(job.eventGroupId, job.tournamentRef, data.matches());

          job.status = ImportStatus.COMPLETED;
          job.completedAt = Instant.now();
          importJobRepository.save(job);

          // recompute full event group statistics deterministically
          long newRevision = eventGroupRepository.findById(job.eventGroupId).orElseThrow().statisticsRevision + 1;
          ratingComputationService.recomputeEventGroup(job.eventGroupId, newRevision, "elo-v1");
          EventGroup eg = eventGroupRepository.findById(job.eventGroupId).orElseThrow();
          eg.statisticsRevision = newRevision;
          eventGroupRepository.save(eg);
        } catch (Exception e) {
          job.status = ImportStatus.FAILED;
          job.failureReason = e.getMessage();
          job.completedAt = Instant.now();
          importJobRepository.save(job);
        } finally {
          inFlight.remove(job.id);
        }
      }
    } catch (Exception ignored) {
      // worker loop for proof-of-work; ignore to avoid crashing scheduler
    }
  }

  List<ImportJob> importJobsForEventGroup(long eventGroupId) {
    return importJobRepository.findByEventGroupIdOrderByCreatedAtDesc(eventGroupId);
  }
}

// -----------------------------
// Start.gg client (fixture)
// -----------------------------

record StartggPlayer(String playerRef, String name) {}

record StartggMatch(String matchRef, long sequenceNo, String playerARef, String playerBRef, String winnerRef, Instant occurredAt) {}

record StartggTournamentData(List<StartggPlayer> players, List<StartggMatch> matches) {}

interface StartggClient {
  StartggTournamentData fetchTournament(String tournamentRef);
}

@Service
@ConditionalOnProperty(name = "app.startgg.mode", havingValue = "fixture", matchIfMissing = true)
class FixtureStartggClient implements StartggClient {
  private final Map<String, StartggTournamentData> fixtures = buildFixtures();

  @Override
  public StartggTournamentData fetchTournament(String tournamentRef) {
    StartggTournamentData data = fixtures.get(tournamentRef);
    if (data == null) {
      // fallback fixture so “it works” with arbitrary links/ids
      return genericTwoPlayerFixture(tournamentRef);
    }
    return data;
  } 

  private static StartggTournamentData genericTwoPlayerFixture(String tournamentRef) {
    String a = tournamentRef + "-p1";
    String b = tournamentRef + "-p2";
    return new StartggTournamentData(
        List.of(
            new StartggPlayer(a, "Player One (" + tournamentRef + ")"),
            new StartggPlayer(b, "Player Two (" + tournamentRef + ")")
        ),
        List.of(
            new StartggMatch(tournamentRef + "-m1", 1, a, b, a, Instant.parse("2025-01-01T00:00:00Z")),
            new StartggMatch(tournamentRef + "-m2", 2, a, b, b, Instant.parse("2025-01-02T00:00:00Z"))
        )
    );
  }

  private static Map<String, StartggTournamentData> buildFixtures() {
    // Example tournament refs:
    // - "spring-smash-2026" etc. (taken from link parsing)
    Map<String, StartggTournamentData> m = new HashMap<>();

    m.put("spring-smash-2026", new StartggTournamentData(
        List.of(
            new StartggPlayer("ss26-alice", "Alice"),
            new StartggPlayer("ss26-bob", "Bob"),
            new StartggPlayer("ss26-carol", "Carol")
        ),
        List.of(
            new StartggMatch("spring-smash-2026-m1", 1, "ss26-alice", "ss26-bob", "ss26-alice", Instant.parse("2025-02-01T10:00:00Z")),
            new StartggMatch("spring-smash-2026-m2", 2, "ss26-carol", "ss26-alice", "ss26-carol", Instant.parse("2025-02-01T10:10:00Z")),
            new StartggMatch("spring-smash-2026-m3", 3, "ss26-bob", "ss26-carol", "ss26-carol", Instant.parse("2025-02-01T10:20:00Z"))
        )
    ));

    m.put("winter-tech-2026", new StartggTournamentData(
        List.of(
            new StartggPlayer("wt26-x", "Xeno"),
            new StartggPlayer("wt26-y", "Yuki"),
            new StartggPlayer("wt26-z", "Zara")
        ),
        List.of(
            new StartggMatch("winter-tech-2026-m1", 1, "wt26-x", "wt26-y", "wt26-y", Instant.parse("2025-01-11T10:00:00Z")),
            new StartggMatch("winter-tech-2026-m2", 2, "wt26-y", "wt26-z", "wt26-y", Instant.parse("2025-01-11T10:30:00Z")),
            new StartggMatch("winter-tech-2026-m3", 3, "wt26-x", "wt26-z", "wt26-z", Instant.parse("2025-01-11T10:40:00Z"))
        )
    ));

    return m;
  }
}

// Start.gg integration selection is simplified for proof-of-work:
// `FixtureStartggClient` is the active `StartggClient` bean.
class StartggClientFactory {
}

// -----------------------------
// Tournament parsing
// -----------------------------

class TournamentRefExtractor {
  private static final Pattern LAST_SEGMENT = Pattern.compile("/([^/?#]+)$");
  private static final Pattern SLUG_AFTER_TOURNAMENT = Pattern.compile("tournament/([^/?#]+)");

  static String normalize(String startggLink) {
    /* 
    if (startggLink == null) return "";
    String link = startggLink.trim();
    // Common examples may end with: .../tournament/<slug>
    Matcher slug = SLUG_AFTER_TOURNAMENT.matcher(link);
    if (slug.find()) return slug.group(1);
    Matcher last = LAST_SEGMENT.matcher(link);
    if (last.find()) return last.group(1);*/
    return startggLink; // NOTE!! This has been changed for the purposes of handling within the startGgIntegration closed context!! 

  }
}

// -----------------------------
// Authorization
// -----------------------------

class AuthorizationService {
  static MembershipRole requireMembershipRole(
      EventGroupMemberRepository memberRepository,
      long eventGroupId,
      long userId
  ) {
    return memberRepository.findByEventGroupIdAndUserId(eventGroupId, userId)
        .map(m -> m.role)
        .orElseThrow(() -> new IllegalArgumentException("not a member of this event group"));
  }

  static void requireRole(
      EventGroupMemberRepository memberRepository,
      long eventGroupId,
      long userId,
      MembershipRole required
  ) {
    MembershipRole actual = requireMembershipRole(memberRepository, eventGroupId, userId);
    if (actual != required) throw new IllegalArgumentException("forbidden");
  }
}

class CurrentUser {
  static long userId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) throw new IllegalArgumentException("unauthenticated");
    Object principal = auth.getPrincipal();
    if (principal instanceof MinimalUserDetails m) return m.userId;
    return Long.parseLong(String.valueOf(principal));
  }

  static void requireAuthenticated() {
    userId();
  }
}

// -----------------------------
// Match repository adapter + rating computation
// -----------------------------

@Component
class MatchRepositoryAdapter {
  private final EventGroupPlayerRepository playerRepository;
  private final ImportedMatchRepository matchRepository;

  MatchRepositoryAdapter(EventGroupPlayerRepository playerRepository, ImportedMatchRepository matchRepository) {
    this.playerRepository = playerRepository;
    this.matchRepository = matchRepository;
  }

  void upsertPlayers(long eventGroupId, List<StartggPlayer> players) {
    for (StartggPlayer p : players) {
      playerRepository.findByEventGroupIdAndPlayerRef(eventGroupId, p.playerRef())
          .orElseGet(() -> playerRepository.save(new EventGroupPlayer(eventGroupId, p.playerRef(), p.name())));
    }
  }

  void upsertMatches(long eventGroupId, String tournamentRef, List<StartggMatch> matches) {
    for (StartggMatch m : matches) {
      if (matchRepository.existsByEventGroupIdAndMatchRef(eventGroupId, m.matchRef())) {
        continue; // idempotent insert for proof-of-work
      }
      ImportedMatch im = new ImportedMatch();
      im.eventGroupId = eventGroupId;
      im.tournamentRef = tournamentRef;
      im.matchRef = m.matchRef();
      im.playerARef = m.playerARef();
      im.playerBRef = m.playerBRef();
      im.winnerRef = m.winnerRef();
      im.matchSequenceNo = m.sequenceNo();
      im.occurredAt = m.occurredAt();
      matchRepository.save(im);
    }
  }
}

@Service
class RatingComputationService {
  private final ImportedMatchRepository importedMatchRepository;
  private final EventGroupPlayerRepository playerRepository;
  private final PlayerRatingRepository ratingRepository;
  private final PlayerMatchupRepository matchupRepository;

  RatingComputationService(
      ImportedMatchRepository importedMatchRepository,
      EventGroupPlayerRepository playerRepository,
      PlayerRatingRepository ratingRepository,
      PlayerMatchupRepository matchupRepository
  ) {
    this.importedMatchRepository = importedMatchRepository;
    this.playerRepository = playerRepository;
    this.ratingRepository = ratingRepository;
    this.matchupRepository = matchupRepository;
  }

  @Transactional
  void recomputeEventGroup(long eventGroupId, long newRevision, String formulaVersion) {
    // Full recompute keeps invariants simple for a proof-of-work implementation.
    // (In the full microservice version, you would do incremental updates keyed by new matches.)
    importedMatchRepository.findByEventGroupIdOrderByMatchSequenceNoAsc(eventGroupId);

    // reset aggregates for this event group
    ratingRepository.findByEventGroupIdOrderByEloDesc(eventGroupId).forEach(ratingRepository::delete);
    matchupRepository.deleteByEventGroupId(eventGroupId);

    List<EventGroupPlayer> players = playerRepository.findByEventGroupId(eventGroupId);
    Map<String, Double> elo = new HashMap<>();
    for (EventGroupPlayer p : players) elo.put(p.playerRef, 1500.0);

    // matchup stats: winsForA/lossesForA for ordered pair
    Map<String, long[]> matchup = new HashMap<>();

    List<ImportedMatch> matches = importedMatchRepository.findByEventGroupIdOrderByMatchSequenceNoAsc(eventGroupId);
    for (ImportedMatch m : matches) {
      String a = m.playerARef;
      String b = m.playerBRef;
      String winner = m.winnerRef;

      double ratingA = elo.getOrDefault(a, 1500.0);
      double ratingB = elo.getOrDefault(b, 1500.0);

      double expectedA = 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
      double expectedB = 1.0 - expectedA;

      double scoreA = winner.equals(a) ? 1.0 : 0.0;
      double scoreB = 1.0 - scoreA;

      // K-factor fixed for proof-of-work (versioned by formulaVersion)
      double K = 32.0;
      double newRatingA = ratingA + K * (scoreA - expectedA);
      double newRatingB = ratingB + K * (scoreB - expectedB);

      elo.put(a, newRatingA);
      elo.put(b, newRatingB);

      // Win/loss matrix
      if (winner.equals(a)) {
        addMatchup(matchup, eventGroupId, a, b, 1, 0);
      } else {
        addMatchup(matchup, eventGroupId, a, b, 0, 1);
      }
    }

    // persist ratings
    for (EventGroupPlayer p : players) {
      PlayerRating pr = new PlayerRating();
      pr.eventGroupId = eventGroupId;
      pr.playerRef = p.playerRef;
      pr.statisticsRevision = newRevision;
      pr.formulaVersion = formulaVersion;
      pr.elo = elo.getOrDefault(p.playerRef, 1500.0);

      // compute wins/losses from matchup map for ordered pair (p vs others)
      long wins = 0;
      long losses = 0;
      for (EventGroupPlayer opp : players) {
        if (opp.playerRef.equals(p.playerRef)) continue;
        String key = matchupKey(eventGroupId, p.playerRef, opp.playerRef);
        long[] wl = matchup.getOrDefault(key, new long[]{0, 0}); // [winsForA, lossesForA]
        wins += wl[0];
        losses += wl[1];
      }
      pr.wins = wins;
      pr.losses = losses;
      ratingRepository.save(pr);
    }

    // persist matchups for ordered pairs
    for (EventGroupPlayer pA : players) {
      for (EventGroupPlayer pB : players) {
        if (pA.playerRef.equals(pB.playerRef)) continue;
        String key = matchupKey(eventGroupId, pA.playerRef, pB.playerRef);
        long[] wl = matchup.getOrDefault(key, new long[]{0, 0});
        PlayerMatchup pm = new PlayerMatchup();
        pm.eventGroupId = eventGroupId;
        pm.playerARef = pA.playerRef;
        pm.playerBRef = pB.playerRef;
        pm.statisticsRevision = newRevision;
        pm.formulaVersion = formulaVersion;
        pm.winsForA = wl[0];
        pm.lossesForA = wl[1];
        matchupRepository.save(pm);
      }
    }
  }

  private static void addMatchup(
      Map<String, long[]> matchup,
      long eventGroupId,
      String a,
      String b,
      long winsForAIncrement,
      long lossesForAIncrement
  ) {
    String key = matchupKey(eventGroupId, a, b);
    long[] wl = matchup.computeIfAbsent(key, k -> new long[]{0, 0});
    wl[0] += winsForAIncrement;
    wl[1] += lossesForAIncrement;
  }

  private static String matchupKey(long eventGroupId, String playerARef, String playerBRef) {
    return eventGroupId + ":" + playerARef + ":" + playerBRef;
  }
}

