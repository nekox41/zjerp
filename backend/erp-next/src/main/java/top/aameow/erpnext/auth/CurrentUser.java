package top.aameow.erpnext.auth;

import java.util.List;

public record CurrentUser(long id, String username, String displayName, Long organizationId,
                          String tokenHash, List<String> permissions) { }