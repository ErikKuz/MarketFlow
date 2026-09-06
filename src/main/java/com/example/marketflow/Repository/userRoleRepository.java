package com.example.marketflow.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.marketflow.userRoles.UserRoleId;
import com.example.marketflow.userRoles.UserRolesEntity;

@Repository
public interface userRoleRepository extends JpaRepository<UserRolesEntity,UserRoleId> {
    boolean existsByRoleId(Short roleId);
    @Query(value = """
            SELECT role.name
            FROM roles role
            JOIN user_roles user_role ON user_role.role_id = role.id
            WHERE user_role.user_id = :userId
            """, nativeQuery = true)
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);
}
