package de.mhus.osgi.web.virtualization.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedList;

import de.mhus.lib.core.directory.ResourceNode;
import de.mhus.lib.errors.MException;

public class FileResource extends ResourceNode {

	public enum KEYS {NAME, LENGTH, MODIFIED, TYPE, HIDDEN};
	public enum TYPE {FILE,DIRCTORY,UNKNOWN}
	
	public static final long UNKNOWN_LENGTH = -1;
	
	private FileRootResource root;
	private File file;
	private FileResource parent;

	public FileResource(FileRootResource root, FileResource parent, File file) {
		if (root == null) root = (FileRootResource) this;
		this.root = root;
		this.file = file;
		this.parent = parent;
	}
	
	@Override
	public String[] getPropertyKeys() {
		
		KEYS[] v = KEYS.values();
		String[] out = new String[v.length];
		for (int i = 0; i < v.length; i++)
			out[i] = v[i].name().toLowerCase();
		
		return out;
	}

	@Override
	public ResourceNode getNode(String key) {
		if (key == null) return null;
		if (key.equals("..") || key.equals(".")) return null;
		if (key.indexOf('/') > -1 || key.indexOf('\\') > -1) return null; // only direct children
		// TODO special chars ?!!
		// TODO cache !!!
		File f = new File(file, key);
		if (!f.exists()) return null;
		return new FileResource(root, this, f);
	}

	@Override
	public ResourceNode[] getNodes() {
		LinkedList<ResourceNode> out = new LinkedList<>();
		for (String sub : file.list()) {
			ResourceNode n = getNode(sub);
			if (n != null)
				out.add(n);
		}
		return out.toArray(new ResourceNode[out.size()]);
	}

	@Override
	public ResourceNode[] getNodes(String key) {
		ResourceNode n = getNode(key);
		if (n == null) return new ResourceNode[0];
		return new ResourceNode[] {n};
	}

	@Override
	public String[] getNodeKeys() {
		LinkedList<String> out = new LinkedList<>();
		for (String sub : file.list()) {
			ResourceNode n = getNode(sub);
			if (n != null)
				out.add(sub);
		}
		return out.toArray(new String[out.size()]);
	}

	@Override
	public String getName() throws MException {
		return file.getName();
	}

	@Override
	public InputStream getInputStream(String key) {
		if (file.isDirectory()) return null;
		try {
			return new FileInputStream(file);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public ResourceNode getParent() {
		return parent;
	}

	@Override
	public URL getUrl() {
		return null;
	}

	@Override
	public Object getProperty(String name) {
		KEYS key = KEYS.valueOf(name.toUpperCase());
		switch (key) {
		case NAME:
			return file.getName();
		case LENGTH:
			return file.length();
		case MODIFIED:
			if (file.isFile())
				return file.lastModified();
			else
				return UNKNOWN_LENGTH;
		case TYPE:
			if (file.isFile())
				return TYPE.FILE;
			if (file.isDirectory())
				return TYPE.DIRCTORY;
			return TYPE.UNKNOWN;
		case HIDDEN:
			return file.isHidden();
		}
		if (name.equals("file"))
			return file;
		return null;
	}

	@Override
	public boolean isProperty(String name) {
		try {
			KEYS key = KEYS.valueOf(name.toUpperCase());
			return key != null;
		} catch (Throwable t) {
		}
		return false;
	}

	@Override
	public void removeProperty(String key) {
	}

	@Override
	public void setProperty(String key, Object value) {
	}

	@Override
	public boolean isEditable() {
		return false;
	}

}
