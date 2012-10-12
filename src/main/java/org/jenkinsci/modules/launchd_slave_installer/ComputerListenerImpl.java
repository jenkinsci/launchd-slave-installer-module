package org.jenkinsci.modules.launchd_slave_installer;

import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import org.apache.commons.codec.binary.Base64;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

import javax.inject.Inject;
import java.io.IOException;
import java.security.interfaces.RSAPublicKey;

/**
 * Inserts {@link LaunchdSlaveInstaller} to every slave.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class ComputerListenerImpl extends ComputerListener {
    @Inject
    InstanceIdentity id;

    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        RSAPublicKey key = id.getPublic();
        String instanceId = Util.getDigestOf(new String(Base64.encodeBase64(key.getEncoded()))).substring(0,8);

        c.getChannel().call(new LaunchdSlaveInstaller(instanceId));
    }
}
