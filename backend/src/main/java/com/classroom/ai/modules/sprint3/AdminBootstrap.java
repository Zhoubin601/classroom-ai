package com.classroom.ai.modules.sprint3;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.*;
import org.springframework.beans.factory.annotation.Value;
import com.classroom.ai.modules.auth.entity.*;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
@Component @RequiredArgsConstructor
@org.springframework.core.annotation.Order(35)
public class AdminBootstrap implements ApplicationRunner {
 private final UserAccountRepository accounts;private final PasswordEncoder passwords;
 @Value("${classroom.admin.bootstrap-enabled:false}") private boolean enabled;
 @Value("${classroom.admin.username:}") private String username;
 @Value("${classroom.admin.password:}") private String password;
 public void run(ApplicationArguments args) {
  if(!enabled)return;
  Sprint3Policy.text(username,64,"管理员用户名");
  var existing=accounts.findByUsername(username);
  if(existing.isPresent()){if(existing.get().getRole()!=RoleEnum.ADMIN)throw new IllegalStateException("初始化用户名已被其他角色占用");return;}
  Sprint3Policy.text(password,128,"初始化密码");
  UserAccount a=new UserAccount();a.setUsername(username);a.setRealName("系统管理员");a.setRole(RoleEnum.ADMIN);a.setPassword(passwords.encode(password));
  try{accounts.saveAndFlush(a);}catch(org.springframework.dao.DataIntegrityViolationException ex) {
   if(accounts.findByUsername(username).filter(x->x.getRole()==RoleEnum.ADMIN).isEmpty())throw ex;
  }
 }
}

