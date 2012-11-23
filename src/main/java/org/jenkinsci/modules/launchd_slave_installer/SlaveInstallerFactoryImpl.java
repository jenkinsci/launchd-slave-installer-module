package org.jenkinsci.modules.launchd_slave_installer;

import hudson.Extension;
import hudson.Util;
import hudson.remoting.Callable;
import hudson.slaves.SlaveComputer;
import org.apache.commons.codec.binary.Base64;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jenkinsci.modules.slave_installer.SlaveInstaller;
import org.jenkinsci.modules.slave_installer.SlaveInstallerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.security.interfaces.RSAPublicKey;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlaveInstallerFactoryImpl extends SlaveInstallerFactory {
    @Inject
    InstanceIdentity id;

    @Override
    public SlaveInstaller createIfApplicable(SlaveComputer c) throws IOException, InterruptedException {
        if (c.getChannel().call(new Predicate())) {
            RSAPublicKey key = id.getPublic();
            String instanceId = Util.getDigestOf(new String(Base64.encodeBase64(key.getEncoded()))).substring(0,8);
            return new LaunchdSlaveInstaller(instanceId);
        }
        return null;
    }

    private static class Predicate implements Callable<Boolean, RuntimeException> {
        public Boolean call() throws RuntimeException {
            return new File("/bin/launchctl").exists() || new File("/Library/LaunchDaemons").exists();
        }
        private static final long serialVersionUID = 1L;
    }
}
