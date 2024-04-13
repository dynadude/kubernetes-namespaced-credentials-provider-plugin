package com.cloudbees.jenkins.plugins.kubernetes_namespaced_credentials_provider;

import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.KubernetesCredentialProvider;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import java.io.InvalidObjectException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;
import org.acegisecurity.Authentication;

public class KubernetesCredentialsProviderProxy extends KubernetesCredentialProvider {
    private static final Logger LOG = Logger.getLogger(KubernetesNamespacedCredentialsProvider.class.getName());

    private final KubernetesCredentialProvider superProvider;

    KubernetesCredentialsProviderProxy(KubernetesClient client)
            throws NoSuchMethodException, IllegalAccessException, InvalidObjectException, NoSuchFieldException {
        this();
        setSuperClient(client);
    }

    KubernetesCredentialsProviderProxy() {
        superProvider = new KubernetesCredentialProvider();
    }

    public void setSuperClient(KubernetesClient client) throws NoSuchFieldException, IllegalAccessException {
        Field clientField = KubernetesCredentialProvider.class.getDeclaredField("client");

        clientField.setAccessible(true);

        try {
            clientField.set(superProvider, client);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessException(
                    "The 'client' field of KubernetesCredentialProvider is not correctly set to accessible");
        }
    }

    /**
     * Does not really override getKubernetesClient from
     * KubernetesCredentialProvider but looks like it and is public
     *
     * @return the kubernetes client that was taken from
     *         KubernetesCredentialProvider
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvalidObjectException
     */
    public KubernetesClient getKubernetesClient()
            throws NoSuchMethodException, IllegalAccessException, InvalidObjectException {
        return getSuperClient();
    }

    public NamespacedKubernetesClient getSuperClient()
            throws NoSuchMethodException, IllegalAccessException, InvalidObjectException {
        Method getKubernetesClientMethod = this.getClass().getSuperclass().getDeclaredMethod("getKubernetesClient");

        getKubernetesClientMethod.setAccessible(true);

        Object clientObject = null;
        try {
            clientObject = getKubernetesClientMethod.invoke(this);
        } catch (InvocationTargetException e) {
            throw new IllegalAccessException(
                    "The 'getKubernetesClient' method of KubernetesCredentialProvider is not correctly set to accessible");
        }

        if (!(clientObject instanceof NamespacedKubernetesClient)) {
            throw new InvalidObjectException(
                    "The 'getKubernetesClient' method of KubernetesCredentialProvider is not of type NamespacedKubernetesClient");
        }

        NamespacedKubernetesClient client = (NamespacedKubernetesClient) clientObject;

        return client;
    }

    @Override
    public void startWatchingForSecrets() {
        invokeSuperMethodAndLogOnFailure("startWatchingForSecrets");
    }

    @Override
    public void stopWatchingForSecrets() {
        invokeSuperMethodAndLogOnFailure("stopWatchingForSecrets");
    }

    private void invokeSuperMethodAndLogOnFailure(String methodName) {
        try {
            Method method = KubernetesCredentialProvider.class.getDeclaredMethod(methodName);
            method.setAccessible(true);

            MethodHandle handle = MethodHandles.publicLookup().unreflect(method);

            handle.invoke(superProvider);
        } catch (Throwable e) {
            logExceptionFromReflectedMethod(methodName, e);
        }
    }

    private void logExceptionFromReflectedMethod(String methodName, Throwable e) {
        if (e instanceof InvocationTargetException) {
            LOG.severe("Exception of type '" + e.getClass().getName()
                    + "' Caused by '" + e.getCause() + "' thrown while trying to invoke the '" + methodName
                    + "' method: "
                    + e.getMessage());
        } else {
            LOG.severe("Exception of type '" + e.getClass().getName()
                    + "' thrown while trying to invoke the '" + methodName + "' method: "
                    + e.getMessage());
        }
    }

    @Override
    public <C extends Credentials> List<C> getCredentials(
            Class<C> type, ItemGroup itemGroup, Authentication authentication) {
        return superProvider.getCredentials(type, itemGroup, authentication);
    }

    @Override
    public void eventReceived(Action action, Secret secret) {
        superProvider.eventReceived(action, secret);
    }

    @Override
    public void onClose() {
        superProvider.onClose();
    }

    @Override
    public CredentialsStore getStore(ModelObject object) {
        return superProvider.getStore(object);
    }

    @Override
    public String getIconClassName() {
        return superProvider.getIconClassName();
    }
}
