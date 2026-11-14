package com.anything_but_dotnet.copilot.security;

import com.anything_but_dotnet.copilot.exception.NotFoundException;
import com.anything_but_dotnet.copilot.service.DashboardOperatorService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class OperatorUserDetailsService implements UserDetailsService {
    private final DashboardOperatorService dashboardOperatorService;

    public OperatorUserDetailsService(DashboardOperatorService dashboardOperatorService) {
        this.dashboardOperatorService = dashboardOperatorService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try {
            var operator = dashboardOperatorService.getOperator(email);
            return new OperatorDetailsImpl(
                    operator.operatorItem().email(),
                    operator.operatorItem().status(),
                    operator.passwordHash(),
                    operator.operatorItem().authorities());
        } catch (NotFoundException ex) {
            throw new UsernameNotFoundException(ex.getMessage(), ex);
        }
    }
}
