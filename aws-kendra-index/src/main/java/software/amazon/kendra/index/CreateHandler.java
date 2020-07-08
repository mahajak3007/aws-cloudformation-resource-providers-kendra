package software.amazon.kendra.index;

import software.amazon.awssdk.services.kendra.KendraClient;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.kendra.model.CreateIndexRequest;
import software.amazon.awssdk.services.kendra.model.CreateIndexResponse;
import software.amazon.awssdk.services.kendra.model.UpdateIndexRequest;
import software.amazon.awssdk.services.kendra.model.UpdateIndexResponse;
import software.amazon.awssdk.services.kendra.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class CreateHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<KendraClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        final ResourceModel model = request.getDesiredResourceState();

        // TODO: Adjust Progress Chain according to your implementation
        // https://github.com/aws-cloudformation/cloudformation-cli-java-plugin/blob/master/src/main/java/software/amazon/cloudformation/proxy/CallChain.java

        return ProgressEvent.progress(model, callbackContext)

            // STEP 1 [check if resource already exists]
            // if target API does not support 'ResourceAlreadyExistsException' then following check is required
            // for more information -> https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-test-contract.html
            //.then(progress -> checkForPreCreateResourceExistence(proxy, request, progress))

            // STEP 2 [create progress chain - required for resource creation]
            .then(progress ->
                // If your service API throws 'ResourceAlreadyExistsException' for create requests then CreateHandler can return just proxy.initiate construction
                // STEP 2.0 [initialize a proxy context]
                proxy.initiate("AWS-Kendra-Index::Create", proxyClient, model, callbackContext)
                    .translateToServiceRequest(Translator::translateToCreateRequest)
                    .makeServiceCall(this::createResource)
                    .done((createIndexRequest1, createIndexResponse1, proxyInvocation1, model1, context1) -> {
                        model1.setId(createIndexResponse1.id());
                        return ProgressEvent.defaultInProgressHandler(context1, 0, model);
                    })
            )
             // stabilize
            .then(progress -> stabilize(proxy, proxyClient, progress))
             // STEP 3 [TODO: post create and stabilize update]
            .then(progress ->
                // If your resource is provisioned through multiple API calls, you will need to apply each subsequent update
                // STEP 3.0 [initialize a proxy context]
                proxy.initiate("AWS-Kendra-Index::postCreate", proxyClient, model, callbackContext)
                    // STEP 3.1 [TODO: construct a body of a request]
                    .translateToServiceRequest(Translator::translateToSecondUpdateRequest)
                    // STEP 3.2 [TODO: make an api call]
                    .makeServiceCall(this::postCreate)
                    .progress()
                )
            // STEP 4 [TODO: describe call/chain to return the resource model]
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    /**
     * If your service API is not idempotent, meaning it does not distinguish duplicate create requests against some identifier (e.g; resource Name)
     * and instead returns a 200 even though a resource already exists, you must first check if the resource exists here
     * NOTE: If your service API throws 'ResourceAlreadyExistsException' for create requests this method is not necessary
     * @param proxy Amazon webservice proxy to inject credentials correctly.
     * @param request incoming resource handler request
     * @param progressEvent event of the previous state indicating success, in progress with delay callback or failed state
     * @return progressEvent indicating success, in progress with delay callback or failed state
     */
    /*
    private ProgressEvent<ResourceModel, CallbackContext> checkForPreCreateResourceExistence(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final ProgressEvent<ResourceModel, CallbackContext> progressEvent) {
        final ResourceModel model = progressEvent.getResourceModel();
        final CallbackContext callbackContext = progressEvent.getCallbackContext();
        try {
            new ReadHandler().handleRequest(proxy, request, callbackContext, logger);
            throw new CfnAlreadyExistsException(ResourceModel.TYPE_NAME, Objects.toString(model.getPrimaryIdentifier()));
        } catch (CfnNotFoundException e) {
            logger.log(model.getPrimaryIdentifier() + " does not exist; creating the resource.");
            return ProgressEvent.progress(model, callbackContext);
        }
    }
     */

    /**
     * Implement client invocation of the create request through the proxyClient, which is already initialised with
     * caller credentials, correct region and retry settings
     * @param createIndexRequest the aws service request to create a resource
     * @param proxyClient the aws service client to make the call
     * @return createIndexResponse create resource response
     */
    private CreateIndexResponse createResource(
            final CreateIndexRequest createIndexRequest,
            final ProxyClient<KendraClient> proxyClient) {
        CreateIndexResponse createIndexResponse;
        try {
            createIndexResponse = proxyClient.injectCredentialsAndInvokeV2(createIndexRequest, proxyClient.client()::createIndex);
        } catch (ValidationException e) {
            throw new CfnInvalidRequestException(ResourceModel.TYPE_NAME, e);
        } catch (final AwsServiceException e) {
            /*
             * While the handler contract states that the handler must always return a progress event,
             * you may throw any instance of BaseHandlerException, as the wrapper map it to a progress event.
             * Each BaseHandlerException maps to a specific error code, and you should map service exceptions as closely as possible
             * to more specific error codes
             */
            throw new CfnGeneralServiceException(ResourceModel.TYPE_NAME, e);
        }

        logger.log(String.format("%s successfully created.", ResourceModel.TYPE_NAME));
        return createIndexResponse;
    }

    /**
     * If your resource is provisioned through multiple API calls, you will need to apply each subsequent update
     * step in a discrete call/stabilize chain to ensure the entire resource is provisioned as intended.
     * @param updateIndexRequest the aws service request to create a resource
     * @param proxyClient the aws service client to make the call
     * @return updateIndexResponse create resource response
     */
    private UpdateIndexResponse postCreate(
        final UpdateIndexRequest updateIndexRequest,
        final ProxyClient<KendraClient> proxyClient) {
        UpdateIndexResponse updateIndexResponse;
        try {
            updateIndexResponse = proxyClient.injectCredentialsAndInvokeV2(updateIndexRequest,
                    proxyClient.client()::updateIndex);
        } catch (final AwsServiceException e) {
            /*
             * While the handler contract states that the handler must always return a progress event,
             * you may throw any instance of BaseHandlerException, as the wrapper map it to a progress event.
             * Each BaseHandlerException maps to a specific error code, and you should map service exceptions as closely as possible
             * to more specific error codes
             */
            throw new CfnGeneralServiceException(ResourceModel.TYPE_NAME, e);
        }

        logger.log(String.format("%s successfully updated.", ResourceModel.TYPE_NAME));
        return updateIndexResponse;
    }
}