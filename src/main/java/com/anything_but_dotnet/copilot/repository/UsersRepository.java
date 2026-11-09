package com.anything_but_dotnet.copilot.repository;

import com.anything_but_dotnet.copilot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersRepository extends JpaRepository<User, String> {
}
