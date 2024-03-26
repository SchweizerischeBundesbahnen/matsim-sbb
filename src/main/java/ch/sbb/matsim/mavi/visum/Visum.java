package ch.sbb.matsim.mavi.visum;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.SafeArray;
import com.jacob.com.Variant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Visum {

	private static final Logger log = LogManager.getLogger(Visum.class);

	private final ActiveXComponent visum;

	public Visum(int version) {
		this.visum = new ActiveXComponent("Visum.Visum." + version);
		log.info("starting Visum client");
	}

	public static String[][] getArrayFromAttributeList(int n, ComObject object, String... attributes) {
		String[][] array = new String[n][attributes.length]; //2d array containing all attributes of all nodes
		int j = 0;
		for (String att : attributes) {
			SafeArray a = Dispatch.call(object.dispatch, "GetMultiAttValues", att).toSafeArray();
			int i = 0;
			while (i < n) {
				array[i][j] = a.getString(i, 1);
				i++;
			}
			j++;
		}
		return array;
	}

	public void loadVersion(String path) {
		log.info("loading version " + path);
		Dispatch.call(this.visum, "LoadVersion", new Variant(path));
		log.info("finished loading version");
	}

	public void setFilterCondition(ComObject filter, FilterCondition cond) {
		Dispatch.call(filter.dispatch, "AddCondition", cond.op, cond.complement,
				cond.attribute, cond.comparator, cond.val, cond.position);
	}

	public void initFilter(ComObject filter) {
		Dispatch.call(filter.dispatch, "Init");
	}

	public void useFilter(ComObject filter, boolean val) {
		Dispatch.put(filter.dispatch, "UseFilter", val);
	}

	public ComObject getComObject(String name) {
		return new ComObject(Dispatch.get(this.visum, name).toDispatch());
	}

	public ComObject callComObject(ComObject object, String name) {
		return new ComObject(Dispatch.call(object.dispatch, name).toDispatch());
	}

	public ComObject getComObject(ComObject object, String name) {
		return new ComObject(Dispatch.get(object.dispatch, name).toDispatch());
	}

	public ComObject getNetObject(String netObject) {
		ComObject net = getComObject("Net");
		return new ComObject(Dispatch.get(net.dispatch, netObject).toDispatch());
	}

	public static class ComObject {

		private final Dispatch dispatch;

		public ComObject(Dispatch dispatch) {
			this.dispatch = dispatch;
		}

		public int countActive() {
			return Dispatch.call(this.dispatch, "CountActive").getInt();
		}

		public void callMethod(String method, String... attributes) {
			Dispatch.call(this.dispatch, method, (Object[]) attributes);
		}

		public void callMethod(String method, String attribute, int dataType) {
			Dispatch.call(this.dispatch, method, attribute, dataType);
		}

		public int getNumActiveElements() {
			return Dispatch.call(this.dispatch, "NumActiveElements").getInt();
		}

		public SafeArray getSafeArray(String method) {
			return Dispatch.call(this.dispatch, method).toSafeArray();
		}
	}

	public static class FilterCondition {

		private final int position;
		private final String op;
		private final boolean complement;
		private final String attribute;
		private final int comparator;
		private final String val;

		public FilterCondition(int position, String op, boolean complement, String attribute, int comparator, String val) {
			this.position = position;
			this.op = op;
			this.comparator = comparator;
			this.complement = complement;
			this.attribute = attribute;
			this.val = val;
		}
	}
}