package com.quorum.tessera.launcher;

import com.quorum.tessera.cli.CliDelegate;
import com.quorum.tessera.cli.CliException;
import com.quorum.tessera.cli.CliResult;
import com.quorum.tessera.cli.CliType;
import com.quorum.tessera.config.AppType;
import com.quorum.tessera.config.Config;
import com.quorum.tessera.config.ConfigException;
import com.quorum.tessera.config.cli.PicoCliDelegate;
import com.quorum.tessera.config.util.JaxbUtil;
import com.quorum.tessera.context.RuntimeContext;
import com.quorum.tessera.context.RuntimeContextFactory;
import com.quorum.tessera.discovery.Discovery;
import com.quorum.tessera.server.TesseraServer;
import com.quorum.tessera.service.locator.ServiceLocator;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.JsonException;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.File;
import java.io.FileWriter;
import java.util.*;

/** The main entry point for the application. This just starts up the application in the embedded container. */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static TesseraServer q2tServer;
    public static TesseraServer p2pServer;
    public static TesseraServer thirdPartyServer;

    public static void main(final String... args) throws Exception {
        System.setProperty(CliType.CLI_TYPE_KEY, CliType.CONFIG.name());
        System.setProperty("javax.xml.bind.JAXBContextFactory", "org.eclipse.persistence.jaxb.JAXBContextFactory");
        System.setProperty("javax.xml.bind.context.factory", "org.eclipse.persistence.jaxb.JAXBContextFactory");

        try {

            PicoCliDelegate picoCliDelegate = new PicoCliDelegate();
            LOGGER.debug("Execute PicoCliDelegate with args [{}]", String.join(",", args));
            final CliResult cliResult = picoCliDelegate.execute(args);
            LOGGER.debug("Executed PicoCliDelegate with args [{}].", String.join(",", args));
            CliDelegate.instance().setConfig(cliResult.getConfig().orElse(null));

            cliResult.getConfig().ifPresent(c -> LOGGER.trace("Config {}", JaxbUtil.marshalToStringNoValidation(c)));

            if (cliResult.isSuppressStartup()) {
                System.exit(0);
            }

            if (cliResult.getStatus() != 0) {
                System.exit(cliResult.getStatus());
            }

            final Config config =
                    cliResult
                            .getConfig()
                            .orElseThrow(() -> new NoSuchElementException("No config found. Tessera will not run."));

            // Start legacy spring profile stuff
            final String springProfileWarning =
                    "Warn: Spring profiles will not be supported in future. To start in recover mode use 'tessera recover'";
            if (System.getProperties().containsKey("spring.profiles.active")) {
                System.out.println(springProfileWarning);
                config.setRecoveryMode(System.getProperty("spring.profiles.active").contains("enable-sync-poller"));
            } else if (System.getenv().containsKey("SPRING_PROFILES_ACTIVE")) {
                System.out.println(springProfileWarning);
                config.setRecoveryMode(System.getenv("SPRING_PROFILES_ACTIVE").contains("enable-sync-poller"));
            }

            // Start end spring profile stuff

            final RuntimeContext runtimeContext = RuntimeContextFactory.newFactory().create(config);

            com.quorum.tessera.enclave.EnclaveFactory.create().create(config);

            LOGGER.debug("Creating service locator");
            ServiceLocator serviceLocator = ServiceLocator.create();
            LOGGER.debug("Created service locator {}", serviceLocator);

            Set<Object> services = serviceLocator.getServices();

            LOGGER.debug("Created {} services", services.size());

            services.forEach(o -> LOGGER.debug("Service : {}", o));

            if (runtimeContext.isOrionMode()) {
                LOGGER.info("Starting Tessera in Orion compatible mode");
            }

            final List<TesseraServer> servers = Launcher.create(runtimeContext.isRecoveryMode()).launchServer(config);

            q2tServer =
                    servers.stream()
                            .filter(server -> server.getAppType() != null && server.getAppType() == AppType.Q2T)
                            .findFirst()
                            .orElse(null);
            p2pServer =
                    servers.stream()
                            .filter(server -> server.getAppType() != null && server.getAppType() == AppType.P2P)
                            .findFirst()
                            .orElse(null);
            thirdPartyServer =
                    servers.stream()
                            .filter(server -> server.getAppType() != null && server.getAppType() == AppType.THIRD_PARTY)
                            .findFirst()
                            .orElse(null);

            runtimeContext.setP2PServer(p2pServer.getUri());
            Discovery.getInstance().onCreate();

            final String urlString = cliResult.getConfig().get().getJdbcConfig().getUrl();
            final String dirPath = urlString.split(":")[2].split(";")[0];

            System.out.println("Directory: " + dirPath);

            FileWriter fileWriter = new FileWriter(new File(dirPath, "q2tServer.uri"));
            fileWriter.write(q2tServer.getUri().toString());
            fileWriter.flush();
            fileWriter.close();
            fileWriter = new FileWriter(new File(dirPath, "p2pServer.uri"));
            fileWriter.write(p2pServer.getUri().toString());
            fileWriter.flush();
            fileWriter.close();
            fileWriter = new FileWriter(new File(dirPath, "thirdPartyServer.uri"));
            fileWriter.write(thirdPartyServer.getUri().toString());
            fileWriter.flush();
            fileWriter.close();

        } catch (final ConstraintViolationException ex) {
            for (final ConstraintViolation<?> violation : ex.getConstraintViolations()) {
                System.err.println(
                        "ERROR: Config validation issue: "
                                + violation.getPropertyPath()
                                + " "
                                + violation.getMessage());
            }
            System.exit(1);
        } catch (final ConfigException ex) {
            final Throwable cause = ExceptionUtils.getRootCause(ex);

            if (JsonException.class.isInstance(cause)) {
                System.err.println("ERROR: Invalid json, cause is " + cause.getMessage());
            } else {
                System.err.println("ERROR: Configuration exception, cause is " + Objects.toString(cause));
            }
            System.exit(3);
        } catch (final CliException ex) {
            System.err.println("ERROR: CLI exception, cause is " + ex.getMessage());
            System.exit(4);
        } catch (final ServiceConfigurationError ex) {
            Optional<Throwable> e = Optional.of(ex);

            e.map(Throwable::getMessage).ifPresent(System.err::println);

            // get root cause
            while (e.map(Throwable::getCause).isPresent()) {
                e = e.map(Throwable::getCause);
            }

            e.map(Throwable::toString).ifPresent(System.err::println);

            System.exit(5);
        } catch (final Throwable ex) {
            LOGGER.debug(null, ex);
            if (Arrays.asList(args).contains("--debug")) {
                ex.printStackTrace();
            } else {
                if (Optional.ofNullable(ex.getMessage()).isPresent()) {
                    System.err.println("ERROR: Cause is " + ex.getMessage());
                } else {
                    System.err.println("ERROR: In class " + ex.getClass().getSimpleName());
                }
            }

            System.exit(2);
        }
    }
}
