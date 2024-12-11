package ca.yukon.aem.core.models;

import ca.yukon.aem.core.utils.UserUtils;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.SearchResult;
import java.util.*;
import javax.annotation.PostConstruct;
import javax.jcr.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Required;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Model(adaptables = Resource.class)
public class UserNodeModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserNodeModel.class);

    @SlingObject
    private ResourceResolver resourceResolver;

    @OSGiService
    @Required
    private QueryBuilder queryBuilder;

    private String rcId = "";
    protected List<String> groups;


    private Map<String, String> propertyMap = new HashMap<>();

    private ArrayList<String> xdpPaths = new ArrayList<>();
    private String sessionUserId = "";
    private String userPath = "";
    private boolean isLoggedIn = false;
    private ArrayList<String> rcGroups;

    @PostConstruct
    protected void init() {
        try {
            Authorizable user = UserUtils.getUser(resourceResolver);
            this.rcId = UserUtils.findRcId(user);
            this.groups = UserUtils.listGroups(resourceResolver);

            Session session = resourceResolver.adaptTo(Session.class);
            String userPath = user.getPath();

            if (session != null && session.nodeExists(userPath + "/profile")) {
                Node profileNode = session.getNode(userPath + "/profile");
                PropertyIterator properties = profileNode.getProperties();

                this.propertyMap.put("path", userPath);
                while (properties.hasNext()) {
                    Property property = properties.nextProperty();
                    if (!property.isMultiple()) {
                        this.propertyMap.put(property.getName(), property.getString());
                    }
                }

                if (this.groups != null && this.groups.size() > 0) {
                    this.propertyMap.put("groups", this.groups.toString());
                }
            }

            try {
                this.sessionUserId = user.getID();
                this.userPath = userPath;
            } catch (Exception e) {
                this.sessionUserId = "";
            }

            this.isLoggedIn =
                    StringUtils.isNotBlank(this.sessionUserId) && !StringUtils.equalsIgnoreCase(this.sessionUserId, "anonymous");
            this.rcGroups = UserUtils.listRcGroups(resourceResolver, queryBuilder);
        } catch (Exception e) {
            LOGGER.error("Failed to get user information: ", e);
        }
    }

    public Map<String, String> getPropertyMap() {
        return this.propertyMap;
    }

    public ArrayList<String> getXdpPaths() {
        return xdpPaths;
    }

    public String getSessionUserId() {
        return sessionUserId;
    }

    public String getUserPath() {
        return userPath;
    }

    public boolean getIsLoggedIn() {
        return isLoggedIn;
    }

    public String getRcGroups() {
        return rcGroups.toString();
    }
}
