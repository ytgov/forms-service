package ca.yukon.aem.core.utils;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import java.util.*;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.ResourceResolver;

public class UserInfoUtils {

    public static Authorizable getUser(ResourceResolver resourceResolver)
            throws RepositoryException, RuntimeException {
        Session session = resourceResolver.adaptTo(Session.class);
        UserManager userManager = resourceResolver.adaptTo(UserManager.class);

        if (userManager == null || session == null) {
            throw new RuntimeException("Couldn't get usermanager or session.");
        }

        Authorizable user = userManager.getAuthorizable(session.getUserID());

        if (user == null) {
            throw new RuntimeException("Couldn't get user.");
        }
        return user;
    }

    public static List<String> listGroups(ResourceResolver resourceResolver)
            throws RepositoryException, RuntimeException {
        Session session = resourceResolver.adaptTo(Session.class);
        UserManager userManager = resourceResolver.adaptTo(UserManager.class);

        List<String> result = new ArrayList<>();

        if (userManager == null || session == null) {
            throw new RuntimeException("Couldn't get usermanager or session.");
        }

        Authorizable user = userManager.getAuthorizable(session.getUserID());
        Iterator<Group> groupsItr = user.declaredMemberOf();

        while (groupsItr.hasNext()) {
            Group grp = groupsItr.next();
            String grpId = grp.getID();
            result.add(grpId);
        }

        return result;
    }

    public static ArrayList<String> listRcGroups(ResourceResolver resourceResolver, QueryBuilder queryBuilder)
            throws RepositoryException, RuntimeException {
        Session session = resourceResolver.adaptTo(Session.class);

        ArrayList<String> result = new ArrayList<>();
        Map<String, String> map = new HashMap<String, String>();
        map.put("path", "/home/groups");
        map.put("type", "rep:Group");
        map.put("1_property", "rep:principalName");
        map.put("1_property.1_value", "%rc%");  //rc-staff
        map.put("1_property.2_value", "%mymoc%");
        map.put("1_property.3_value", "%cpd%");
        map.put("1_property.operation", "like");

        Query query = queryBuilder.createQuery(PredicateGroup.create(map), session);
        SearchResult searchResult = query.getResult();

        for (Hit hit : searchResult.getHits()) {
            String grpName = hit.getNode().getProperty("rep:authorizableId").getString();
            if (StringUtils.isNotBlank(grpName)) result.add(grpName);
        }

        return result;
    }

    public static boolean isGroupMember(ResourceResolver resourceResolver, Authorizable user, String groupName)
            throws RepositoryException {
        if (StringUtils.isEmpty(groupName) || user == null) {
            return false;
        }

        UserManager userManager = resourceResolver.adaptTo(UserManager.class);

        if (userManager == null) {
            throw new NullPointerException("Couldn't get usermanager.");
        }

        Authorizable group = userManager.getAuthorizable(groupName);
        if (group != null) {
            return ((Group) group).isMember(user);
        }

        return false;
    }
}