package com.zabbix.gateway;

public class TestMXBeanImpl implements TestMXBean {
	
	public Double getSciNotationDouble() {
		return 0.0000000001;
	}
	
	public TestCompositeObject getCompositeObject() {
		return new TestCompositeObject();
	}
	
	public class TestCompositeObject {
		
		public String getValueOne() {
			return "ValueOne";
		}
		
		public String getValueTwo() {
			return "ValueTwo";
		}
		
		public TestSubCompositeObject getSubComposite() {
			return new TestSubCompositeObject();
		}
	}
	
	public class TestSubCompositeObject {
		
		public String getSubValueOne() {
			return "SubValueOne";
		}
		
		public String getSubValueTwo() {
			return "SubValueTwo";
		}
		
	}

}
