Architecture
============

`DRAFT`

This document is a draft on the software architecture for the Weaving Voices Engine. The design emphasize a multi-peer but yet simple and flexible design.

#### MVC Architecture

The system takes a distributed approach to the **Model View Controller** architecture.

The system is built of **multiple agents** referred as **services**. Services can be either **Views** or **Controllers**. Each service have an **internal modular Model** which keeps its attributes in sync with the other services using a **subscription** based approach.

The subscription based model is based around a **publisher** which based on an **attributes dictionary** provides the services with the available attributes on the system they can **subscribe** to.

On the current implementation the publisher serves also as a publishing server pushing the notifications to the subscribed services. This is a functionality that can be later done distributively while keeping the publisher just as an attributes dictionary.

`Notes: Rename Services to something else?`

###Model

####Attributes dictionary

This Attributed dictionary defines the main **data model** used:

* Name
	* Unique identifier of an **attribute** offered by a **service**.
* Value
	* Current value of the **attribute**.
* Net Adress
	* Current network location for the **service** (IP Address and Port)
* Subscribers
	* Collection of NetAdresses of the **services** registered to the **attribute**.

###Actors

####Service

Services are instances running some logic or view, they communicate using a subscription based model.

#####Attributes actions:


* **PUT:** Updates an attribute locally and remotely.

	* Properties:
		* Name
		* Value

	* Actions:
		* Update the **Arbiter attributes dictionary**
		* Update the **Local attributes dictionary**

* **SUBSCRIBE:** Subscribes to an attribute locally or remotely.

	* Properties:
		* Attribute Name

	* Actions:
		* Updates the **Subscribers** for a particular **Name** on the **Arbiter attributes dictionary**
		* Return the current **Attribute**, including the **latest value** for the particular **Name**



####Publisher

The publisher is the subscription manager and updates pusher.

#####Attributes actions:

* **PUBLISH:** Pushes updates on the **Arbiter attributes dictionary** to the registered **Services**.

	* Trigger:
		* An update on the **Arbiter attributes dictionary**.

	* Actions:
		* Pushes the updates on the **Arbiter attributes dictionary** to the **Subscribers** for a specific **Attribute**.
