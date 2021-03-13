package de.mhus.karaf.commands.impl;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;

import de.mhus.lib.core.IProperties;
import de.mhus.lib.core.M;
import de.mhus.lib.core.config.IConfig;
import de.mhus.lib.core.config.MConfig;
import de.mhus.lib.core.operation.DefaultTaskContext;
import de.mhus.lib.core.operation.Operation;
import de.mhus.lib.core.operation.OperationResult;
import de.mhus.osgi.api.karaf.AbstractCmd;
import de.mhus.osgi.api.services.OperationManager;

@Command(
        scope = "mhus",
        name = "operastion-execute",
        description = "Execute operation")
@Service
public class CmdOperationExecute extends AbstractCmd {

    @Argument(
            index = 0,
            name = "id/pathVersion",
            required = true,
            description = "Ident of the operation",
            multiValued = false)
    String name;

    @Argument(
            index = 1,
            name = "paramteters",
            required = false,
            description = "Parameters",
            multiValued = true)
    String[] parameters;

    @Option(
            name = "-c",
            aliases = "--config",
            description = "Config parameters",
            required = false,
            multiValued = true
    		)
    private String[] config;
    
    @Option(
            name = "-t",
            aliases = "--try",
            description = "Try only mode",
            required = false)
    private boolean test = false;

    @Option(
            name = "-l",
            aliases = "--load",
            description = "First parameter is a json content",
            required = false)
    private boolean isLoad = false;
    
	@Override
	public Object execute2() throws Exception {
		
		OperationManager api = M.l(OperationManager.class);
		if (api == null) {
			System.out.println("OperationManager not found");
			return null;
		}
		Operation oper = api.getOperation(name);
		if (oper == null) {
			System.out.println("Operation not found");
			return null;
		}
		
		IConfig param = null;
		if (isLoad) {
			param = IConfig.readConfigFromString(parameters[0]);
		} else {
			param = new MConfig();
			param.putAll( IProperties.explodeToMProperties(parameters) );
		}
		
		IConfig cfg = null;
		if (config != null) {
			cfg = new MConfig();
			cfg.putAll( IProperties.explodeToMProperties(config) );
		}
		
		DefaultTaskContext context = new DefaultTaskContext(this.getClass());
		context.setTestOnly(test);
		context.setParameters(param);
		context.setConfig(cfg);
		
		OperationResult res = oper.doExecute(context);

		System.out.println("Successful : " + res.isSuccessful());
		System.out.println("Return Code: " +res.getReturnCode());
		System.out.println("Message    : " + res.getMsg());
		
		return res.getResult();
	}

}
