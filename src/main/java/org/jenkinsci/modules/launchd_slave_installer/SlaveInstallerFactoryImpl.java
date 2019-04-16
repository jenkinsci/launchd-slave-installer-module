package org.jenkinsci.modules.launchd_slave_installer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.remoting.Channel;
import org.apache.commons.codec.binary.Base64;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jenkinsci.modules.slave_installer.SlaveInstaller;
import org.jenkinsci.modules.slave_installer.SlaveInstallerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import jenkins.security.MasterToSlaveCallable;

/**
 * {@link SlaveInstallerFactory} for launchd.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlaveInstallerFactoryImpl extends SlaveInstallerFactory {
    @Inject
    InstanceIdentity id;

    @Override
    public SlaveInstaller createIfApplicable(Channel c) throws IOException, InterruptedException {
        if (c.call(new Predicate())) {
            RSAPublicKey key = id.getPublic();
            // TODO use standard method when available
            String instanceId = Util.getDigestOf(new String(Base64.encodeBase64(key.getEncoded()), StandardCharsets.UTF_8)).substring(0,8);
            return new LaunchdSlaveInstaller(instanceId);
        }
        return null;
    }

    private static class Predicate extends MasterToSlaveCallable<Boolean, RuntimeException> {
        @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
        @Override
        public Boolean call() throws RuntimeException {
            return new File("/bin/launchctl").exists() || new File("/System/Library/LaunchDaemons").exists();
        }
        private static final long serialVersionUID = 1L;
    }
}
