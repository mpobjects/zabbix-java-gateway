package com.zabbix.gateway;

public class TestMXBeanImpl implements TestMXBean {
	
	@Override
	public Double getSciNotationDouble() {
		return 0.0000000001;
	}
	
	@Override
	public TestCompositeObject getCompositeObject() {
		return new TestCompositeObject();
	}
	
	@Override
	public long[] getLongArray() {
		return new long[]{1L, 2L, 3L};
	}
	
	@Override
	public int[] getEmptyArray() {
		return new int[]{};
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
