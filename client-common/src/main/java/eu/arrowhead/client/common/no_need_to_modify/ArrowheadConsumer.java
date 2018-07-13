package eu.arrowhead.client.common.no_need_to_modify;

import eu.arrowhead.client.common.no_need_to_modify.exception.ArrowheadException;
import eu.arrowhead.client.common.no_need_to_modify.misc.TypeSafeProperties;
import eu.arrowhead.client.common.no_need_to_modify.model.ArrowheadService;
import eu.arrowhead.client.common.no_need_to_modify.model.ArrowheadSystem;
import eu.arrowhead.client.common.no_need_to_modify.model.OrchestrationResponse;
import eu.arrowhead.client.common.no_need_to_modify.model.ServiceRequestForm;
import org.glassfish.jersey.SslConfigurator;

import javax.net.ssl.SSLContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.util.Map;

public abstract class ArrowheadConsumer {
    protected final TypeSafeProperties props = Utility.getProp("app.properties");
    private final String orchestratorUrl;
    private final ArrowheadSystem consumer;
    protected boolean isSecure = false;

    public ArrowheadConsumer(String[] args, String systemName) {
        //Prints the working directory for extra information. Working directory should always contain a config folder with the app.properties file!
        System.out.println("Working directory: " + System.getProperty("user.dir"));

        for (String arg : args) {
            if (arg.equals("-tls")) {
                isSecure = true;
            }
        }

        if (isSecure) {
            SslConfigurator sslConfig = SslConfigurator.newInstance().trustStoreFile(props.getProperty("truststore"))
                    .trustStorePassword(props.getProperty("truststorepass"))
                    .keyStoreFile(props.getProperty("keystore")).keyStorePassword(props.getProperty("keystorepass"))
                    .keyPassword(props.getProperty("keypass"));
            SSLContext sslContext = sslConfig.createSSLContext();
            Utility.setSSLContext(sslContext);
        }

        //Compile the URL for the orchestration request.
        orchestratorUrl = getOrchestratorUrl(args);

        /*
            ArrowheadSystem: systemName, (address, port, authenticationInfo)
            Since this Consumer skeleton will not receive HTTP requests (does not povide any services on its own),
            the address, port and authenticationInfo fields can be set to anything.
        */
        consumer = new ArrowheadSystem(systemName, "null", 0, "null");
    }

    protected static <T> T requestEntity(String method, String providerUrl, Object payload, Class<T> aClass) {
      Response response = request(method, providerUrl, payload);

      T obj;
      try {
        obj = response.readEntity(aClass);
      } catch (RuntimeException e) {
        System.out.println("Provider did not send response in a parsable format.");
        e.printStackTrace();
        throw e;
      }
      return obj;
    }

    protected static Response request(String method, String providerUrl, Object payload) {
      Response response = Utility.sendRequest(providerUrl, method, payload);
      final Response.StatusType statusInfo = response.getStatusInfo();
      if (statusInfo.getFamily() != Response.Status.Family.SUCCESSFUL) {
        final int statusCode = statusInfo.getStatusCode();
          final String reasonPhrase = statusInfo.getReasonPhrase();
          System.out.println("GOT " + statusCode + " " + reasonPhrase);
        throw new ArrowheadException(reasonPhrase, statusCode);
      }
      return response;
    }

    /**
     * Sends the orchestration request to the Orchestrator, and compiles the URL for the first provider received from
     * the OrchestrationResponse
     */
    protected String sendOrchestrationRequest(ServiceRequestForm srf) {
        //Sending a POST request to the orchestrator (URL, method, payload)
        Response postResponse = Utility.sendRequest(orchestratorUrl, "POST", srf);
        //Parsing the orchestrator response
        OrchestrationResponse orchResponse = postResponse.readEntity(OrchestrationResponse.class);
        System.out.println("Orchestration Response payload: " + Utility.toPrettyJson(null, orchResponse));
        if (orchResponse.getResponse().isEmpty()) {
            throw new ArrowheadException("Orchestrator returned with 0 Orchestration Forms!");
        }

        //Getting the first provider from the response
        ArrowheadSystem provider = orchResponse.getResponse().get(0).getProvider();
        String serviceURI = orchResponse.getResponse().get(0).getServiceURI();
        //Compiling the URL for the provider
        UriBuilder ub = UriBuilder.fromPath("").host(provider.getAddress()).scheme("http");
        if (serviceURI != null) {
            ub.path(serviceURI);
        }
        if (provider.getPort() > 0) {
            ub.port(provider.getPort());
        }
        if (orchResponse.getResponse().get(0).getService().getServiceMetadata().containsKey("security")) {
            ub.scheme("https");
            ub.queryParam("token", orchResponse.getResponse().get(0).getAuthorizationToken());
            ub.queryParam("signature", orchResponse.getResponse().get(0).getSignature());
        }
        System.out.println("Received provider system URL: " + ub.toString());
        return ub.toString();
    }

    /**
     * Gets the correct URL where the orchestration requests needs to be sent (from app.properties config file +
     * command line argument)
     */
    private String getOrchestratorUrl(String[] args) {
        String orchAddress = props.getProperty("orch_address", "0.0.0.0");

        if (isSecure) {
            int orchSecurePort = props.getIntProperty("orch_secure_port", 8441);
            return Utility.getUri(orchAddress, orchSecurePort, "orchestrator/orchestration", true, false);
        } else {
            int orchInsecurePort = props.getIntProperty("orch_insecure_port", 8440);
            return Utility.getUri(orchAddress, orchInsecurePort, "orchestrator/orchestration", false, false);
        }
    }

    protected ServiceRequestForm buildServiceRequestForm(ArrowheadService service, Map<String, Boolean> orchestrationFlags) {
      ServiceRequestForm srf = new ServiceRequestForm.Builder(consumer).requestedService(service).orchestrationFlags(orchestrationFlags).build();
      System.out.println("Service Request payload: " + Utility.toPrettyJson(null, srf));
      return srf;
    }
}
