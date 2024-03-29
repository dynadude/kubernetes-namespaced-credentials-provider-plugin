package com.cloudbees.jenkins.plugins.kubernetes_namespaced_credentials_provider;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import java.io.InvalidObjectException;
import java.lang.reflect.Field;

public class CredentialsIdChanger {
    public static void changeId(Credentials creds, String newId)
            throws InvalidObjectException, NoSuchFieldException, IllegalAccessException {
        if (!(creds instanceof BaseStandardCredentials)) {
            throw new InvalidObjectException("Credentials supplied to " + CredentialsIdChanger.class.getName()
                    + " are not instances of BaseStandardCredentials");
        }

        doChangeId(creds, newId);
    }

    private static void doChangeId(Credentials creds, String newId)
            throws NoSuchFieldException, IllegalAccessException {
        Field idField = BaseStandardCredentials.class.getDeclaredField("id");

        idField.setAccessible(true);

        try {
            idField.set(creds, newId);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessException(
                    "The 'id' field of BaseStandardCredentials is not correctly set to accessible");
        }
    }
}
