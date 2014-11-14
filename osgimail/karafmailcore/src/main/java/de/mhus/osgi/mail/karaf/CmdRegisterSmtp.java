package de.mhus.osgi.mail.karaf;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.util.Properties;

import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.commands.Action;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;

import de.mhus.lib.core.MString;
import de.mhus.lib.core.console.ConsoleTable;
import de.mhus.osgi.mail.core.SendQueue;
import de.mhus.osgi.mail.core.SendQueueManager;
import de.mhus.osgi.mail.core.SmtpSendQueue;

@Command(scope = "mail", name = "registersmtp", description = "Register a new SMTP queue")
public class CmdRegisterSmtp implements Action {

	private SendQueueManager admin;

	@Argument(index=0, name="name", required=true, description="Queue Name", multiValued=false)
    String name;
	@Argument(index=1, name="property", required=true, description="properties key=value and file:<file> to load from", multiValued=true)
    String[] properties;

	@Override
	public Object execute(CommandSession session) throws Exception {
		
		SendQueue q = admin.getQueue(name);
		if (q != null) {
			System.out.println("Queue already exists");
			return null;
		}
		
		Properties p = new Properties();
		for (String item : properties) {
			if (item.indexOf('=') > 1) {
				p.put(MString.beforeIndex(item, '='), MString.afterIndex(item, '='));
			} else 
			if (item.startsWith("file:")) {
				File f = new File(item.substring(5));
				if (f.exists() && f.isFile()) {
					FileInputStream fis = new FileInputStream(f);
					p.load(fis);
					fis.close();
				} else {
					System.out.println("Can't load file " + f.getName());
				}
			}
		}
		
		SmtpSendQueue smtp = new SmtpSendQueue(name, p);
		admin.registerQueue(smtp);

		System.out.println("OK");
		
		return null;
	}
	
	public void setAdmin(SendQueueManager admin) {
		this.admin = admin;
	}

}
