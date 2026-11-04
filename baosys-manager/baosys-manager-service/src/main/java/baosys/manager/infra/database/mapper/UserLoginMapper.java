package baosys.manager.infra.database.mapper;

import baosys.manager.infra.database.entity.LoginSessionRow;
import baosys.manager.infra.database.entity.UserLoginEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.util.CollectionUtils;

public interface UserLoginMapper extends BaseMapper<UserLoginEntity> {
    @Select("SELECT id FROM user_login WHERE authorization_id=#{authorizationId} AND expires_at>clock_timestamp()")
    List<Long> findActiveIdsByAuthorization(@Param("authorizationId") String authorizationId);

    @Select("""
        SELECT u.id FROM user_login l
        JOIN user_info u ON u.id=l.user_info_id
        JOIN oauth2_authorization a ON a.id=l.authorization_id
        WHERE l.id=#{loginId} AND l.expires_at>clock_timestamp() AND u.enabled=true
          AND l.client_id=#{tokenClientId} AND l.client_id=#{internalClientId}
          AND a.principal_name=CAST(u.id AS VARCHAR) AND a.authorization_grant_type=#{grant}
        """)
    List<Long> findInternalAccountIds(@Param("loginId") long loginId,
        @Param("tokenClientId") String tokenClientId, @Param("internalClientId") String internalClientId,
        @Param("grant") String grant);

    @org.apache.ibatis.annotations.Insert("""
        INSERT INTO user_login(user_info_id, client_id, authorization_id, authentication_session_id,
            login_client, expires_at, created_at, updated_at, created_by, updated_by)
        VALUES(#{userId},#{clientId},#{authorizationId},#{sessionId},#{agent},#{expiry},
            clock_timestamp(),clock_timestamp(),'oauth2','oauth2')
        ON CONFLICT (authorization_id) DO UPDATE SET expires_at=EXCLUDED.expires_at, updated_at=clock_timestamp()
        """)
    void saveProjection(@Param("userId") long userId, @Param("clientId") String clientId,
        @Param("authorizationId") String authorizationId, @Param("sessionId") String sessionId,
        @Param("agent") String agent, @Param("expiry") OffsetDateTime expiry);

    /**
     * 只返回未过期登录；userId 优先用于精确定位，否则按用户名模糊过滤。
     */
    @Select(
        """
        <script>
        SELECT ul.id,
               ui.id AS user_id,
               ui.user_name,
               ui.user_permissions,
               ul.login_client,
               ul.created_at AS login_at,
               ul.expires_at
          FROM user_login ul
          JOIN user_info ui ON ui.id = ul.user_info_id
         WHERE ul.expires_at &gt; #{now}
        <if test="userId != null">
           AND ui.id = #{userId}
        </if>
        <if test="userId == null and userName != null and userName != ''">
           AND LOWER(ui.user_name) LIKE LOWER(CONCAT('%', #{userName}, '%'))
        </if>
         ORDER BY ul.created_at DESC
        </script>
        """
    )
    IPage<LoginSessionRow> selectActiveSessions(
        Page<LoginSessionRow> page,
        @Param("userId") Long userId,
        @Param("userName") String userName,
        @Param("now") OffsetDateTime now
    );

    default List<UserLoginEntity> selectActiveByUserId(Long userId, OffsetDateTime now) {
        return selectList(
            Wrappers.<UserLoginEntity>lambdaQuery()
                .eq(UserLoginEntity::getUserInfoId, userId)
                .gt(UserLoginEntity::getExpiresAt, now)
                .orderByDesc(UserLoginEntity::getCreatedAt)
        );
    }

    default Optional<UserLoginEntity> findActiveByIdAndUserId(
        Long id,
        Long userId,
        OffsetDateTime now
    ) {
        return Optional.ofNullable(
            selectOne(
                Wrappers.<UserLoginEntity>lambdaQuery()
                    .eq(UserLoginEntity::getId, id)
                    .eq(UserLoginEntity::getUserInfoId, userId)
                    .gt(UserLoginEntity::getExpiresAt, now)
            )
        );
    }

    default Set<Long> selectActiveUserIds(Collection<Long> userIds, OffsetDateTime now) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Set.of();
        }
        List<Object> values = selectObjs(
            Wrappers.<UserLoginEntity>lambdaQuery()
                .select(UserLoginEntity::getUserInfoId)
                .in(UserLoginEntity::getUserInfoId, userIds)
                .gt(UserLoginEntity::getExpiresAt, now)
                .groupBy(UserLoginEntity::getUserInfoId)
        );
        Set<Long> result = new HashSet<>();
        values.forEach(value -> result.add(((Number) value).longValue()));
        return Set.copyOf(result);
    }

    default long countActiveByUserId(Long userId, OffsetDateTime now) {
        return selectCount(
            Wrappers.<UserLoginEntity>lambdaQuery()
                .eq(UserLoginEntity::getUserInfoId, userId)
                .gt(UserLoginEntity::getExpiresAt, now)
        );
    }

    default int deleteByUserId(Long userId) {
        return delete(
            Wrappers.<UserLoginEntity>lambdaQuery().eq(UserLoginEntity::getUserInfoId, userId)
        );
    }
}
