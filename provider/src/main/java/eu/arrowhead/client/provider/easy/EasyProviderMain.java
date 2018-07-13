/*
 *  Copyright (c) 2018 AITIA International Inc.
 *
 *  This work is part of the Productive 4.0 innovation project, which receives grants from the
 *  European Commissions H2020 research and innovation programme, ECSEL Joint Undertaking
 *  (project no. 737459), the free state of Saxony, the German Federal Ministry of Education and
 *  national funding authorities from involved countries.
 */

package eu.arrowhead.client.provider.easy;

import eu.arrowhead.client.common.no_need_to_modify.ArrowheadProps;
import eu.arrowhead.client.common.no_need_to_modify.ArrowheadProvider;
import eu.arrowhead.client.common.no_need_to_modify.Utility;
import eu.arrowhead.client.common.no_need_to_modify.model.*;

import java.util.*;

/* This version of the ProviderMain class has some extra functionalities, that are not mandatory to have:
    1) Secure (HTTPS) mode
    2) Authorization registration
    3) Orchestration Store registration
    4) Get payloads from JSON files
 */
public class EasyProviderMain extends ArrowheadProvider {

  public static void main(String[] args) {
    new EasyProviderMain(args);
  }

  private EasyProviderMain(String[] args) {
    super(args,
            new Class[] {EasyTemperatureResource.class},
            new String[] {"eu.arrowhead.client.common"});

    boolean needAuth = false;
    boolean needOrch = false;
    boolean fromFile = false;

    for (String arg : args) {
      switch (arg) {
        case "-ff":
          fromFile = true;
          break;
        case "-auth":
          needAuth = true;
          break;
        case "-orch":
          needOrch = true;
          break;
      }
    }

    if (isSecure && needOrch) {
      throw new ServiceConfigurationError("The Store registration feature can only be used in insecure mode!");
    }

    final ServiceRegistryEntry srEntry = loadSrEntry(fromFile);
    registerToServiceRegistry(srEntry);

    if (needOrch) {
      final IntraCloudAuthEntry authEntry = loadAuthEntry(fromFile);
      registerToAuthorization(authEntry);
    }

    if (needAuth) {
      final List<OrchestrationStore> storeEntry = loadStoreEntry(fromFile);
      registerToStore(storeEntry);
    }

    listenForInput();
  }

  private List<OrchestrationStore> loadStoreEntry(boolean fromFile) {
    List<OrchestrationStore> storeEntry;

    if (fromFile) {
      String storePath = props.getProperty("store_entry");
      storeEntry = Arrays.asList(Utility.fromJson(Utility.loadJsonFromFile(storePath), OrchestrationStore[].class));
    } else {
      storeEntry = ArrowheadProps.getStoreEntry(props, baseUri, isSecure, base64PublicKey);
    }
    System.out.println("Orchestration Store Entry: " + Utility.toPrettyJson(null, storeEntry));

    return storeEntry;
  }

  private IntraCloudAuthEntry loadAuthEntry(boolean fromFile) {
    IntraCloudAuthEntry authEntry;

    if (fromFile) {
      String authPath = props.getProperty("auth_entry");
      authEntry = Utility.fromJson(Utility.loadJsonFromFile(authPath), IntraCloudAuthEntry.class);
    } else {
      authEntry = ArrowheadProps.getAuthEntry(base64PublicKey, baseUri, isSecure, props);
    }
    System.out.println("IntraCloud Auth Entry: " + Utility.toPrettyJson(null, authEntry));

    return authEntry;
  }

  private ServiceRegistryEntry loadSrEntry(boolean fromFile) {
    ServiceRegistryEntry srEntry;

    if (fromFile) {
      String srPath = props.getProperty("sr_entry");
      srEntry = Utility.fromJson(Utility.loadJsonFromFile(srPath), ServiceRegistryEntry.class);
    } else {
      srEntry = ArrowheadProps.getServiceRegistryEntry(props, baseUri, isSecure, base64PublicKey);
    }
    System.out.println("Service Registry Entry: " + Utility.toPrettyJson(null, srEntry));

    return srEntry;
  }
}
