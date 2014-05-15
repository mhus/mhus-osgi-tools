package de.hfo.test.ws.ws_model;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.Style;

@WebService
@SOAPBinding(style = Style.RPC)
public interface WSService {

	@WebMethod
	void addEntity(WSEntity entity);
	
	@WebMethod
	WSEntity[] getAll();
	
	@WebMethod
	void remvoeEntity(WSEntity entity);
	
}
