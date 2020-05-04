/**
 * Copyright 2018 Mike Hummel
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mhus.karaf.commands.mhus;

import java.io.File;
import java.util.UUID;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;

import de.mhus.lib.core.MFile;
import de.mhus.lib.core.MPassword;
import de.mhus.lib.core.MValidator;
import de.mhus.lib.core.console.Console;
import de.mhus.lib.core.console.ConsoleTable;
import de.mhus.lib.core.crypt.Blowfish;
import de.mhus.lib.core.crypt.pem.PemBlock;
import de.mhus.lib.core.crypt.pem.PemUtil;
import de.mhus.lib.core.keychain.DefaultEntry;
import de.mhus.lib.core.keychain.MKeychain;
import de.mhus.lib.core.keychain.MKeychainUtil;
import de.mhus.lib.core.keychain.MutableVaultSource;
import de.mhus.lib.core.keychain.KeyEntry;
import de.mhus.lib.core.keychain.KeychainSource;
import de.mhus.lib.core.keychain.KeychainSourceFromSecFile;
import de.mhus.lib.core.parser.ParseException;
import de.mhus.osgi.api.karaf.AbstractCmd;

@Command(scope = "mhus", name = "keychain", description = "Vault Manipulation")
@Service
public class CmdKeychain extends AbstractCmd {

    private static final long MAX_FILE_LENGTH = 1024 * 1024; // max 1 MB

    @Argument(
            index = 0,
            name = "cmd",
            required = true,
            description =
                    "Create keys with openssl: openssl genrsa -out private.pem 8192\n"
                            + "Commands:\n"
                            + " sources\n"
                            + " list\n"
                            + " get <id/name>\n"
                            + " set <id> <type> <name> <description>\n"
                            + " settype <id> <type>\n"
                            + " setname <id> <name>\n"
                            + " setdesc <id> <description>\n"
                            + " move <id> <to source>   - clone and remove from old source\n"
                            + " clone <id> <to source>  - copy with the same id\n"
                            + " copy <id> <to source>   - copy with a new id\n"
                            + " import <file> [name] [description]\n"
                            + " add <type> <name> <description> <value>\n"
                            + " remove <id>\n"
                            + " save\n"
                            + " load\n"
                            + " addfilesource <file> <passphrase>\n"
                            + " removesource <id>\n"
                            + " encodepasswordrot13 <clear>\n"
                            + " encodepasswordwithkey <key id> <clear>\n"
                            + " encodepasswordmd5 <clear>\n"
                            + " decodepassword <encoded password>\n"
                            + "",
            multiValued = false)
    String cmd;

    @Argument(
            index = 1,
            name = "paramteters",
            required = false,
            description = "Parameters",
            multiValued = true)
    String[] parameters;

    @Option(name = "-s", aliases = "--source", description = "Set vault source name")
    String sourcename = null;

    @Option(name = "-f", aliases = "--force", description = "Overwrite existing", required = false)
    boolean force = false;

    @Option(
            name = "-id",
            description = "Optiona a existing uuid to import in raw mode",
            required = false)
    String id = null;

    @Option(
            name = "-p",
            aliases = {"--passphrase"},
            description = "Define a passphrase if required",
            required = false,
            multiValued = false)
    String passphrase = null;

    @Override
    public Object execute2() throws Exception {
        MKeychain vault = MKeychainUtil.loadDefault();

        if (cmd.equals("copy")) {
            KeyEntry entry = null;
            if (sourcename != null)
                entry = vault.getSource(sourcename).getEntry(UUID.fromString(parameters[0]));
            else entry = vault.getEntry(UUID.fromString(parameters[0]));
            if (entry == null) {
                System.out.println("*** Entry not found");
                return null;
            }
            entry =
                    new DefaultEntry(
                            entry.getType(),
                            entry.getName(),
                            entry.getDescription(),
                            entry.getValue());
            vault.getSource(parameters[1]).getEditable().addEntry(entry);
            System.out.println("OK " + entry.getId());
        } else if (cmd.equals("move")) {
            if (sourcename == null) sourcename = MKeychain.SOURCE_DEFAULT;
            KeychainSource source = vault.getSource(sourcename);
            if (source == null) {
                System.out.println("*** Source not found " + sourcename);
                return null;
            }
            MutableVaultSource editable = source.getEditable();
            if (editable == null) {
                System.out.println("*** Source is not editable " + sourcename);
                return null;
            }
            KeyEntry entry = source.getEntry(UUID.fromString(parameters[0]));
            if (entry == null) {
                System.out.println("*** Entry not found in source " + sourcename);
                return null;
            }
            vault.getSource(parameters[1]).getEditable().addEntry(entry);
            editable.removeEntry(UUID.fromString(parameters[0]));
            System.out.println("OK");
        } else if (cmd.equals("clone")) {
            KeyEntry entry = null;
            if (sourcename != null)
                entry = vault.getSource(sourcename).getEntry(UUID.fromString(parameters[0]));
            else entry = vault.getEntry(UUID.fromString(parameters[0]));
            if (entry == null) {
                System.out.println("*** Entry not found");
                return null;
            }
            vault.getSource(parameters[1]).getEditable().addEntry(entry);
            System.out.println("OK");
        } else if (cmd.equals("sources")) {
            ConsoleTable out = new ConsoleTable(tblOpt);
            out.setHeaderValues("Source", "Info", "Mutable", "MemoryBased");
            for (String sourceName : vault.getSourceNames()) {
                KeychainSource source = vault.getSource(sourceName);

                boolean isMutable = false;
                boolean isMemoryBased = false;
                try {
                    MutableVaultSource mutable = source.getEditable();
                    isMutable = mutable != null;
                    isMemoryBased = mutable.isMemoryBased();
                    if (isMutable) {}
                } catch (Throwable t) {
                }
                out.addRowValues(sourceName, source, isMutable, isMemoryBased);
            }
            out.print(System.out);
        } else if (cmd.equals("list")) {
            if (sourcename == null) {
                ConsoleTable out = new ConsoleTable(tblOpt);
                out.setHeaderValues("Source", "Id", "Type", "Name", "Description");
                for (String sourceName : vault.getSourceNames()) {
                    try {
                        KeychainSource source = vault.getSource(sourceName);
                        for (UUID id : source.getEntryIds()) {
                            KeyEntry entry = source.getEntry(id);
                            out.addRowValues(
                                    sourceName,
                                    id,
                                    entry.getType(),
                                    entry.getName(),
                                    entry.getDescription());
                        }
                    } catch (Throwable t) {
                        log().d(sourceName, t);
                    }
                }
                out.print(System.out);
            } else {
                KeychainSource source = vault.getSource(sourcename);
                if (source == null) {
                    System.out.println("*** Source not found!");
                    return null;
                }
                ConsoleTable out = new ConsoleTable(tblOpt);
                out.setHeaderValues("Source", "Id", "Type", "Name", "Description");
                for (UUID id : source.getEntryIds()) {
                    KeyEntry entry = source.getEntry(id);
                    out.addRowValues(
                            sourcename,
                            id,
                            entry.getType(),
                            entry.getName(),
                            entry.getDescription());
                }
                out.print(System.out);
            }
        } else if (cmd.equals("add") || cmd.equals("addraw")) {
            if (sourcename == null) sourcename = MKeychain.SOURCE_DEFAULT;
            KeychainSource source = vault.getSource(sourcename);
            if (source == null) {
                System.out.println("*** Source not found!");
                return null;
            }
            MutableVaultSource mutable = source.getEditable();

            String type = parameters[0];
            String name = parameters[1];
            String description = parameters[2];
            String content = parameters[3];

            if (passphrase != null && content.contains("-----BEGIN CIPHER-----")) {
                if ("".equals(passphrase)) {
                    System.out.print("Passphrase: ");
                    System.out.flush();
                    passphrase = Console.get().readPassword();
                }
                PemBlock pem = PemUtil.parse(content);
                if (id == null && pem.containsKey(PemBlock.IDENT))
                    id = pem.getString(PemBlock.IDENT);
                content = pem.getBlock();
                content = Blowfish.decrypt(content, passphrase);
            }

            if (type.equals("")) type = MKeychainUtil.getType(content);
            PemBlock pem = null;
            try {
                pem = PemUtil.parse(content);
            } catch (ParseException e) {
            }
            if (pem != null && description.equals("")) {
                description = pem.getString(PemBlock.DESCRIPTION, "");
                if (id == null) id = pem.getString(PemBlock.IDENT, null);
            }
//            if (pem != null && name.equals("")) 
//                name = pem.getString("");

            DefaultEntry entry = new DefaultEntry(type, name, description, content);
            if (id != null)
                entry =
                        new DefaultEntry(
                                UUID.fromString(id),
                                entry.getType(),
                                entry.getName(),
                                entry.getDescription(),
                                entry.getValue());

            if (mutable.getEntry(entry.getId()) != null) {
                if (force) mutable.removeEntry(entry.getId());
                else {
                    System.out.println("*** Entry already exists " + entry.getId());
                    return null;
                }
            }

            mutable.addEntry(entry);
            System.out.println("Created " + entry + ". Don't forget to save!");
            return entry.getId().toString();
        } else if (cmd.equals("import")) {
            if (sourcename == null) sourcename = MKeychain.SOURCE_DEFAULT;
            KeychainSource source = vault.getSource(sourcename);
            if (source == null) {
                System.out.println("*** Source not found!");
                return null;
            }
            MutableVaultSource mutable = source.getEditable();

            File file = new File(parameters[0]);
            if (!file.exists()) {
                System.out.println("*** File not found");
                return null;
            }
            if (!file.isFile()) {
                System.out.println("*** File is not an file");
                return null;
            }
            if (file.length() > MAX_FILE_LENGTH) {
                System.out.println("*** File to large to load");
                return null;
            }

            String name = (parameters.length > 1 ? parameters[1] + ";" : "");
            String desc = (parameters.length > 2 ? parameters[2] + ";" : "") + file.getName();
            String content = MFile.readFile(file);
            if (passphrase != null && content.contains("-----BEGIN CIPHER-----")) {
                if ("".equals(passphrase)) {
                    System.out.print("Passphrase: ");
                    System.out.flush();
                    passphrase = Console.get().readPassword();
                }
                PemBlock pem = PemUtil.parse(content);
                if (id == null && pem.containsKey(PemBlock.IDENT))
                    id = pem.getString(PemBlock.IDENT);
                desc = pem.getString(PemBlock.DESCRIPTION, desc);
                content = pem.getBlock();
                content = Blowfish.decrypt(content, passphrase);
            }

            String type = MKeychainUtil.getType(content);
            KeyEntry entry = new DefaultEntry(type, name, desc, content);

            if (id != null)
                entry =
                        new DefaultEntry(
                                UUID.fromString(id),
                                entry.getType(),
                                entry.getName(),
                                entry.getDescription(),
                                entry.getValue());

            if (mutable.getEntry(entry.getId()) != null) {
                if (force) mutable.removeEntry(entry.getId());
                else {
                    System.out.println("*** Entry already exists " + entry.getId());
                    return null;
                }
            }
            mutable.addEntry(entry);
            System.out.println(
                    "Created " + entry.getId() + " " + entry.getType() + ". Don't forget to save!");
            return entry.getId().toString();
        } else if (cmd.equals("save")) {
            if (sourcename == null) sourcename = MKeychain.SOURCE_DEFAULT;
            KeychainSource source = vault.getSource(sourcename);
            if (source == null) {
                System.out.println("*** Source not found!");
                return null;
            }
            MutableVaultSource mutable = source.getEditable();
            mutable.doSave();
            System.out.println("OK");
        } else if (cmd.equals("load")) {
            if (sourcename == null) sourcename = MKeychain.SOURCE_DEFAULT;
            KeychainSource source = vault.getSource(sourcename);
            if (source == null) {
                System.out.println("Source not found!");
                return null;
            }
            MutableVaultSource mutable = source.getEditable();
            mutable.doLoad();
            System.out.println("OK");
        } else if (cmd.equals("get")) {
            KeyEntry entry = null;
            if (sourcename != null) {
                if (MValidator.isUUID(parameters[0]))
                    entry = vault.getSource(sourcename).getEntry(UUID.fromString(parameters[0]));
                else entry = vault.getSource(sourcename).getEntry(parameters[0]);
            } else {
                if (MValidator.isUUID(parameters[0]))
                    entry = vault.getEntry(UUID.fromString(parameters[0]));
                else entry = vault.getEntry(parameters[0]);
            }
            if (entry == null) {
                System.out.println("*** Entry not found");
                return null;
            }
            System.out.println("Id         : " + entry.getId());
            System.out.println("Type       : " + entry.getType());
            System.out.println("Name       : " + entry.getName());
            System.out.println("Description: " + entry.getDescription());
            System.out.println(" Value");
            System.out.println("-------");
            System.out.println(entry.getValue().value());
            System.out.println("-------");
            return entry;
        } else if (cmd.equals("setname")) {
            if (sourcename == null) sourcename = MKeychain.SOURCE_DEFAULT;
            KeychainSource source = vault.getSource(sourcename);
            if (source == null) {
                System.out.println("*** Source not found!");
                return null;
            }
            MutableVaultSource mutable = source.getEditable();
            KeyEntry entry = mutable.getEntry(UUID.fromString(parameters[0]));
            DefaultEntry newEntry =
                    new DefaultEntry(
                            entry.getId(),
                            entry.getType(),
                            parameters[1],
                            entry.getDescription(),
                            "");
            mutable.updateEntry(newEntry);
            System.out.println("Set");
        } else if (cmd.equals("setdesc")) {
            if (sourcename == null) sourcename = MKeychain.SOURCE_DEFAULT;
            KeychainSource source = vault.getSource(sourcename);
            if (source == null) {
                System.out.println("*** Source not found!");
                return null;
            }
            MutableVaultSource mutable = source.getEditable();
            KeyEntry entry = mutable.getEntry(UUID.fromString(parameters[0]));
            DefaultEntry newEntry =
                    new DefaultEntry(
                            entry.getId(), entry.getType(), entry.getName(), parameters[1], "");
            mutable.updateEntry(newEntry);
            System.out.println("Set");
        } else if (cmd.equals("settype")) {
            if (sourcename == null) sourcename = MKeychain.SOURCE_DEFAULT;
            KeychainSource source = vault.getSource(sourcename);
            if (source == null) {
                System.out.println("*** Source not found!");
                return null;
            }
            MutableVaultSource mutable = source.getEditable();
            KeyEntry entry = mutable.getEntry(UUID.fromString(parameters[0]));
            DefaultEntry newEntry =
                    new DefaultEntry(
                            entry.getId(),
                            parameters[1],
                            entry.getName(),
                            entry.getDescription(),
                            "");
            mutable.updateEntry(newEntry);
            System.out.println("Set");
        } else if (cmd.equals("set")) {
            if (sourcename == null) sourcename = MKeychain.SOURCE_DEFAULT;
            KeychainSource source = vault.getSource(sourcename);
            if (source == null) {
                System.out.println("*** Source not found!");
                return null;
            }
            MutableVaultSource mutable = source.getEditable();
            KeyEntry entry = mutable.getEntry(UUID.fromString(parameters[0]));
            DefaultEntry newEntry =
                    new DefaultEntry(
                            entry.getId(), parameters[1], parameters[2], parameters[3], "");
            mutable.updateEntry(newEntry);
            System.out.println("Set");
        } else if (cmd.equals("remove") || cmd.equals("delete")) {
            if (sourcename == null) sourcename = MKeychain.SOURCE_DEFAULT;
            KeychainSource source = vault.getSource(sourcename);
            if (source == null) {
                System.out.println("*** Source not found!");
                return null;
            }
            MutableVaultSource mutable = source.getEditable();

            mutable.removeEntry(UUID.fromString(parameters[0]));
            System.out.println("OK");
        } else if (cmd.equals("addfilesource")) {
            KeychainSourceFromSecFile source =
                    new KeychainSourceFromSecFile(new File(parameters[0]), parameters[1]);
            vault.registerSource(source);
            System.out.println("Registered " + source);
        } else if (cmd.equals("removesource")) {
            vault.unregisterSource(parameters[0]);
            System.out.println("OK");
        } else if (cmd.equals("encodepasswordrot13")) {
            System.out.println(MPassword.encode(MPassword.METHOD.ROT13, parameters[0]));
        } else if (cmd.equals("encodepasswordwithkey")) {
            System.out.println(
                    MPassword.encode(MPassword.METHOD.RSA, parameters[1], parameters[0]));
        } else if (cmd.equals("encodepasswordmd5")) {
            System.out.println(MPassword.encode(MPassword.METHOD.HASH_MD5, parameters[1]));
        } else if (cmd.equals("decodepassword")) {
            System.out.println(MPassword.decode(parameters[0]));
        } else
            System.out.println("Command unknown");

        return null;
    }
}
