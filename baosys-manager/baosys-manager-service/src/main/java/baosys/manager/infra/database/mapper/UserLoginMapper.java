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
