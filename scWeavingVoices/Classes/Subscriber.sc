/*
Redo of Publisher-Subscriber using broadcast.

Wed, Aug  6 2014, 04:13 EEST

For documentation: 

The functionalithy is organized in the following categories (groups of methods)

1. Init subscriber : Initializing the communication process (setup of IPs and responders)
2. Actions triggered locally by the app: get, put, request a subscription or a value.
3. Actions triggered remotely from other nodes (via OSC): 
   - return a requested value, 
   - update a received attribute value
   - subcribe 
   - unsubscribe
*/


Subscriber {
	
	classvar <localAddress;
	classvar <broadcastAddress;
	classvar <userName;
	classvar <allApps; // dict of running apps by IP/port and user name

	var <name;
	var <port; // communication port.  Defaults to port of this sclang apps' local address
	var <userName;   // name of this user (Symbol).  Defaults system login name of this user
	var <attributes; // dictionary of attributes holding shared values

	// ================================================================
	// message names for network communication protocol
	var <requestMsg = '/request';   // request a value, optionally subscribe to attribute
	var <unsubscribeMsg = '/unsubscribe'; // unsubscribe from attribute
	var <offerMsg = '/offer';  // broadcast new attribute to net, offering it to subscribers
	var <updateMsg = '/update'; // receive changes in value of attribute from senders

	*initClass {
		NetAddr.broadcastFlag = true;
		StartUp add: {
			this.registerApp;
			Subscriber();
		};
	}

	*registerApp {
		broadcastAddress = NetAddr(NetAddr.getBroadcastIp, NetAddr.localAddr.port);
		localAddress = NetAddr(NetAddr.getLocalIp, NetAddr.localAddr.port);
		userName = "whoami".unixCmdGetStdOut;
		allApps = Dictionary(); // tests for equality of addresses (not identity)
		OSCFunc({ | msg, time, address |
			allApps[address] = msg[1];
			address.sendMsg(\runningApp, userName);
			this.changed(\apps);
		}, \newApp);
		OSCFunc({ | msg, time, address |
			allApps[address] = msg[1];
			this.changed(\apps);
		}, \runningApp);
		broadcastAddress.sendMsg(\newApp, userName);
	}

	*new { | name, port, userName |
		if (userName.isNil) { this.registerApp };
		^Registry(this, name ?? { this.defaultName }, {
			super.new.initSubscriber(name, 
				port ?? { NetAddr.localAddr.port }, 
				userName ?? { "whoami".unixCmdGetStdOut.split($\n).first });
		});
	}

	*defaultName { ^'data' }

	initSubscriber { | argName, argPort |
		name = argName;
		port = argPort;
		attributes = IdentityDictionary();
		this.initSubscriptionResponses;
	}

	// ================================================================
	// responses to requests from subscribers or remote subsciptions
	// ================================================================

	initSubscriptionResponses {
		// var <>updateMsg = '/update';
		OSCFunc({ | msg, time, address |
			// msg[0] -> updateMsg
			// msg[1] -> Name of attribute updated
			// msg[2...] -> Data sent (value of attribute)
			var attributeName, attribute, data;
			attributeName = msg[1];
			data = msg[2..];
			attribute = this.getAttribute(attributeName);
			this.setAttributeData(data);
		}, updateMsg);

		// var <>requestMsg = '/request';
		OSCFunc({ | msg, time, address |
			// msg[0] -> requestMsg
			// msg[1] -> Name of attribute requested
			// msg[2] -> flag: if true then subscribe
			var attributeName, subscribe_p, attribute, data;
			if (address != localAddress) { // never subscribe to yourself
				attributeName = msg[1];
				subscribe_p = msg[2];
				attribute = this.getAttribute(attributeName);
				if (subscribe_p == 1) {
					attribute addSubscriber: address;
					this.changed(\subcriber, address);
				};
				data = attribute.data;
				data !? { address.sendMsg(\update, attributeName, *data) };
			}
		}, requestMsg);

		// var <offerMsg = '/offer';  
		// receive offer to subscribe to attribute: If attribute exists,
		// then subscribe to sender.
		OSCFunc({ | msg, time, address |
			// msg[0] -> offerMsg
			// msg[1] -> Name of attribute offered
			// msg[2..] -> data
			var attributeName, attribute, data;
			attributeName = msg[1];
			attribute = attributes[attributeName];
			attribute !? {
				this.setAttributeData(attribute, attributeName, data);
				address.sendMsg(requestMsg, attributeName, true); // subscribe to new sender
			};
		}, offerMsg);	


		// var <>unsubscribeMsg = '/unsubscribe';
		OSCFunc({ | msg, time, address |
			// msg[0] -> requestMsg
			// msg[1] -> Name of attribute requested
			// msg[2] -> flag: if true then subscribe
			var attributeName, attribute;
			attributeName = msg[1];
			attribute = attributes[attributeName];
			attribute !? { attribute unsubscribe: address };
		}, unsubscribeMsg)
	}

	// ================================================================
	// access and setting of attributes
	// ================================================================

	setAttributeData { | attribute, attributeName, data |
		attribute.data = data;
		this.changed(attributeName, *data)
	}

	*get { | attributeName | ^this.new.get(attributeName) }

	get { | attributeName, defaultValue |
		/*  --- if attribute exists, get its local cached value.
			--- Else:
			   (1) create attribute, setting its value to nil.
			   (2) - request the value from network and when received set its value.
			       - subscribe to the attribute remotely.
			--- Finally: return the current value of the attribute
 		*/
		var attribute;
		attribute = this.getAttribute(attributeName, defaultValue);
		^attribute.data;
	}

	getAttribute { | attributeName, defaultValue |
		var attribute;
		attribute = attributes[attributeName];
		attribute ?? {
			attribute = Attribute(attributeName);
			attributes[attributeName] = attribute;
			defaultValue !? { attribute.data = defaultValue; };
			this.request(attributeName, subscribe: true);
			this.changed(\attribute, attributeName);
		};
		^attribute;
	}

	// request value, optionally subcribe to all nodes in system except self
	*request {  | attributeName, subscribe = false |
		this.new.request(attributeName, subscribe);
	}

	request { | attributeName, subscribe = false |
		broadcastAddress.sendMsg(requestMsg, attributeName, subscribe);
	}

	*put { | attributeName, value, broadcast = true |
		this.new.put(attributeName, value, broadcast)
	}

	put { | attributeName, data, broadcast = true |
		var attribute;
		attribute = this.getAttribute(attributeName);
		this.setAttributeData(attribute, attributeName, data);
		if (attribute.notOffered) { this.offer(attribute, data) };
		if (broadcast) { attribute.broadcast };
	}

	offer { | attribute, data |
		broadcastAddress.sendMsg(offerMsg, attribute.name, *data);
		attribute.notOffered = false;
	}

	*unsubscribe { | attributeName |
		this.new.unsubscribe(attributeName);
	}

	unsubscribe { | attributeName |
		var attribute;
		attribute = attributes[attributeName];
		attribute !? {
			attribute.sender.sendMsg(unsubscribeMsg);
		}
	}

	// ================================================================
	// Interface to local logic: Actions to be executed when an attribute is updated
	// ================================================================

	*addUpdateAction { | listener, attributeName, action |
		this.new.addUpdateAction(listener, attributeName, action);
	}

	addUpdateAction { | listener, attributeName, action |
		this.get(attributeName);
		listener.addNotifier(this, attributeName, action);
	}

	*removeUpdateAction { | listener, attributeName |
		this.new.removeUpdateAction(listener, attributeName);
	}

	removeUpdateAction { | listener, attributeName |
		listener.removeNotifier(this, attributeName);
	}

	*addCodeAction { | listener, userName, action |
		listener.addNotifier(CodeSubscriber(), userName, action);
	}

	*removeCodeAction { | listener, userName | 
		listener.removeNotifier(CodeSubscriber(), userName);
	}

}

CodeSubscriber : Subscriber {

	var <requestUserNameMsg = '/requestUserName'; // Request/receive id (name) of user

	*defaultName { ^'code' }

	initSubscriber { | argName, argPort |
		requestMsg = '/requestUser';   // request code from a user, optionally subscribe
		unsubscribeMsg = '/unsubscribeUser'; // unsubscribe from user
		offerMsg = '/offerUser';  // broadcast new user to net, offering to subscribers
		updateMsg = '/code';  // receive code from a user on the net
		super.initSubscriber(argName, argPort);
		OSCFunc({ | msg, time, address |
			// msg[0] -> requestUserIdMsg
			// msg[1] -> Name (ID) of user
			var newUser, attribute;
			newUser = msg[1];
			this.getAttribute(newUser); // creates + subscribes to new user
		}, requestUserNameMsg);		
		this.getAllUsers;
	}

	getAllUsers {
		broadcastAddress.sendMsg(requestUserNameMsg, userName);
	}

}


Attribute {
	/*  Data item stored in any node of the network.
		Broadcast changes in your data to all subscribed nodes in the system */
	var <name, <sender, <>data, <time, <subscribers, <>notOffered = true;

	*new { | name, sender, data, time, subscribers |
		^this.newCopyArgs(name, sender ?? Subscriber.localAddress,
			data, time ?? { Date.getDate.rawSeconds }, Set()
		);
	}

	setData { | argData senderAddr |
		data = argData;
		senderAddr ?? { senderAddr = Subscriber.localAddress };
		if (sender.notNil and: { sender != senderAddr }) {
			this.changeSender(senderAddr);
		};
	}

	changeSender { | newSender |
		postf(
			"Sender change in attribute: %.\nOld sender: %\nNew sender: %\n",
			name, sender, newSender
		);
		sender = newSender;
	}

	broadcast { subscribers do: _.sendMsg('/update', name, *data); }

	addSubscriber { | subscriber |
		if (subscriber != Subscriber.localAddress) { subscribers add: subscriber };
	}

	removeSubscriber { | subscriber | subscribers remove: subscriber; }
}
