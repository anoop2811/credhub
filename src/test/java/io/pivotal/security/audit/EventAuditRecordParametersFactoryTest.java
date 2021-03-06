package io.pivotal.security.audit;

import io.pivotal.security.request.PermissionEntry;
import io.pivotal.security.request.PermissionOperation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static io.pivotal.security.audit.AuditingOperationCode.ACL_DELETE;
import static io.pivotal.security.audit.AuditingOperationCode.ACL_UPDATE;
import static io.pivotal.security.request.PermissionOperation.READ;
import static io.pivotal.security.request.PermissionOperation.READ_ACL;
import static io.pivotal.security.request.PermissionOperation.WRITE;
import static io.pivotal.security.request.PermissionOperation.WRITE_ACL;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.samePropertyValuesAs;

@RunWith(JUnit4.class)
public class EventAuditRecordParametersFactoryTest {

  @Test
  public void createPermissionsEventAuditParameters_returnsPermissionsEventsList() {
    String credentialName = "/test";
    List<PermissionEntry> permissionEntryList = asList(
        new PermissionEntry("actor1", asList(READ, WRITE)),
        new PermissionEntry("actor2", asList(READ_ACL, WRITE_ACL))
    );

    List<EventAuditRecordParameters> permissionsEventAuditParameters = EventAuditRecordParametersFactory
        .createPermissionsEventAuditParameters(
            ACL_UPDATE,
            credentialName,
            permissionEntryList);

    assertThat(permissionsEventAuditParameters, containsInAnyOrder(
        samePropertyValuesAs(new EventAuditRecordParameters(ACL_UPDATE, credentialName, READ, "actor1")),
        samePropertyValuesAs(new EventAuditRecordParameters(ACL_UPDATE, credentialName, WRITE, "actor1")),
        samePropertyValuesAs(new EventAuditRecordParameters(ACL_UPDATE, credentialName, READ_ACL, "actor2")),
        samePropertyValuesAs(new EventAuditRecordParameters(ACL_UPDATE, credentialName, WRITE_ACL, "actor2"))
    ));
  }

  @Test
  public void createPermissionEventAuditRecordParameters_returnsPermissionsEventsList() {
    String credentialName = "/test";
    List<PermissionOperation> operations = newArrayList(READ, WRITE);

    List<EventAuditRecordParameters> permissionsEventAuditParameters = EventAuditRecordParametersFactory
        .createPermissionEventAuditRecordParameters(
            ACL_DELETE,
            credentialName,
            "actor1",
            operations
        );

    assertThat(permissionsEventAuditParameters, containsInAnyOrder(
        samePropertyValuesAs(new EventAuditRecordParameters(ACL_DELETE, credentialName, READ, "actor1")),
        samePropertyValuesAs(new EventAuditRecordParameters(ACL_DELETE, credentialName, WRITE, "actor1"))
    ));
  }
}
