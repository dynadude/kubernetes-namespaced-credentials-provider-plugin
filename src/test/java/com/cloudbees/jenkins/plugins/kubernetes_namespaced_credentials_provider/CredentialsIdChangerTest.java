package com.cloudbees.jenkins.plugins.kubernetes_namespaced_credentials_provider;

import static org.junit.Assert.assertEquals;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import java.io.InvalidObjectException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CredentialsIdChangerTest {
    @Test
    public void testChangeId() throws InvalidObjectException, NoSuchFieldException, IllegalAccessException {
        IdCredentials cred = createCredentialsWithId("test");

        assertEquals("test", cred.getId());

        CredentialsIdChanger.changeId(cred, "newId");

        assertEquals("newId", cred.getId());
    }

    public IdCredentials createCredentialsWithId(String id) {
        String description = "";
        String username = description;
        String password = description;

        return new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, id, description, username, password);
    }
}
